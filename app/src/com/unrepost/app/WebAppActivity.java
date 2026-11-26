package com.unrepost.app;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Vibrator;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.Toast;

/**
 * Aplikasi utama (versi website): menampilkan situs Unrepost dari aset lokal
 * (file:///android_asset/web/index.html) di dalam WebView, dan menyediakan
 * jembatan native singkat (buka TikTok, notifikasi, layar tetap menyala).
 *
 * <p>Situsnya sendiri yang mengurus login & proses hapus repost lewat sesi
 * TikTok. Tidak ada izin aksesibilitas yang perlu diaktifkan.</p>
 */
public class WebAppActivity extends Activity implements DomRunner.Host {

    private WebView web;
    private FrameLayout root;
    private WebView tiktokWeb;
    private final android.os.Handler main = new android.os.Handler(android.os.Looper.getMainLooper());
    private boolean renderedCrash;
    private DomRunner domRunner;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Jaring pengaman terakhir: jangan pernah "keluar sendiri" tanpa pesan.
        Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            @Override
            public void uncaughtException(Thread thread, Throwable error) {
                try {
                    String msg = "Aplikasi terhenti: " + error.getClass().getSimpleName()
                            + (error.getMessage() == null ? "" : " — " + error.getMessage());
                    android.util.Log.e("Unrepost", msg, error);
                    Toast.makeText(getApplicationContext(), msg, Toast.LENGTH_LONG).show();
                } catch (Throwable ignored) {
                }
                try {
                    Thread.sleep(600L);
                } catch (InterruptedException ignored) {
                }
                android.os.Process.killProcess(android.os.Process.myPid());
            }
        });

        root = new FrameLayout(this);
        root.setBackgroundColor(Color.parseColor("#0B0B0F"));

        web = newWebView();

        root.addView(web, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        setContentView(root);

        web.loadUrl("file:///android_asset/web/index.html");
    }

    /** Buat WebView utama lengkap dengan setelan + jembatan JS. */
    @SuppressLint("SetJavaScriptEnabled")
    private WebView newWebView() {
        WebView w = new WebView(this);
        WebSettings s = w.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setAllowFileAccess(true);
        s.setAllowContentAccess(true);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        s.setSupportZoom(false);
        s.setMediaPlaybackRequiresUserGesture(true);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
        s.setCacheMode(WebSettings.LOAD_DEFAULT);
        if (Build.VERSION.SDK_INT >= 26) {
            s.setSafeBrowsingEnabled(true);
        }

        CookieManager cm = CookieManager.getInstance();
        cm.setAcceptCookie(true);
        if (Build.VERSION.SDK_INT >= 21) {
            cm.setAcceptThirdPartyCookies(w, true);
        }

        w.setBackgroundColor(Color.parseColor("#0B0B0F"));
        w.setWebChromeClient(new MyChromeClient());
        w.setWebViewClient(new MyWebClient());
        w.addJavascriptInterface(new Bridge(this), "UnrepostNative");

        return w;
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Beri tahu situs: pengguna mungkin baru kembali dari halaman login TikTok.
        main.postDelayed(new Runnable() {
            @Override
            public void run() {
                try {
                    CookieManager.getInstance();
                } catch (Exception ignored) {
                }
                js2("window.UnrepostResume && window.UnrepostResume();");
            }
        }, 600L);
    }

    @Override
    protected void onDestroy() {
        try {
            if (tiktokWeb != null) {
                root.removeView(tiktokWeb);
                tiktokWeb.destroy();
                tiktokWeb = null;
            }
            if (web != null) {
                root.removeView(web);
                web.destroy();
                web = null;
            }
        } catch (Exception ignored) {
        }
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        if (tiktokWeb != null) {
            if (tiktokWeb.canGoBack()) {
                tiktokWeb.goBack();
                return;
            }
            closeTikTok();
            return;
        }
        if (web != null && web.canGoBack()) {
            web.goBack();
            return;
        }
        super.onBackPressed();
    }

    // ------------------------------------------------------------- WebView TikTok

    /** WebView kedua untuk login/verifikasi TikTok (punya cookie sendiri). */
    @SuppressLint("SetJavaScriptEnabled")
    private void openTikTok(String url) {
        // WebView ringan: tanpa zoom, tanpa auto-window, media butuh gestur → memori kecil.
        if (tiktokWeb == null) {
            tiktokWeb = new WebView(this);
            WebSettings s = tiktokWeb.getSettings();
            s.setJavaScriptEnabled(true);
            s.setDomStorageEnabled(true);
            s.setDatabaseEnabled(true);
            s.setLoadWithOverviewMode(true);
            s.setUseWideViewPort(true);
            s.setSupportZoom(false);
            s.setBuiltInZoomControls(false);
            s.setDisplayZoomControls(false);
            s.setJavaScriptCanOpenWindowsAutomatically(false);
            s.setSupportMultipleWindows(false);
            s.setMediaPlaybackRequiresUserGesture(true);
            s.setBlockNetworkImage(false);
            s.setCacheMode(WebSettings.LOAD_DEFAULT);
            s.setDomStorageEnabled(true);
            s.setUserAgentString(s.getUserAgentString().replace("; wv)", ")"));
            CookieManager cm = CookieManager.getInstance();
            cm.setAcceptCookie(true);
            if (Build.VERSION.SDK_INT >= 21) {
                cm.setAcceptThirdPartyCookies(tiktokWeb, true);
            }
            tiktokWeb.setBackgroundColor(Color.parseColor("#000000"));
            // Jembatan untuk snapshot DOM (dipakai otomasi "hapus repost").
            if (domRunner == null) {
                domRunner = new DomRunner(this, this, 25, 12000L, false, "");
            }
            tiktokWeb.addJavascriptInterface(domRunner.new Bridge(), "UnrepostDom");
            tiktokWeb.setWebChromeClient(new MyChromeClient());
            tiktokWeb.setWebViewClient(new WebViewClient() {
                @Override
                public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                    return false;
                }

                @Override
                public void onPageFinished(WebView view, String url) {
                    super.onPageFinished(view, url);
                    DomRunner.noteUrl(url);
                    // setelah login selesai, UI situs langsung tahu
                    js2("window.UnrepostResume && window.UnrepostResume();");
                    // suntikkan snapshoter DOM setiap halaman TikTok selesai dimuat
                    try {
                        view.evaluateJavascript(DomRunner.bootstrapScript(WebAppActivity.this), null);
                    } catch (Exception ignored) {
                    }
                    if (domRunner != null) {
                        domRunner.snapNow();
                    }
                }
            });
            root.addView(tiktokWeb, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            Toast.makeText(this, "Login TikTok di sini, lalu tekan kembali", Toast.LENGTH_LONG).show();
        }
        tiktokWeb.setVisibility(View.VISIBLE);
        tiktokWeb.loadUrl(url != null && !url.isEmpty() ? url : "https://www.tiktok.com/login");
    }

    private void closeTikTok() {
        if (tiktokWeb != null) {
            root.removeView(tiktokWeb);
            tiktokWeb.destroy();
            tiktokWeb = null;
            if (web != null) {
                web.evaluateJavascript("window.Unrepost && window.Unrepost.openTiktokDone();", null);
            }
        }
    }

    // ------------------------------------------------------------------ klien

    private class MyWebClient extends WebViewClient {
        @Override
        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
            Uri uri = request.getUrl();
            return handleUrl(uri);
        }

        @Override
        public boolean shouldOverrideUrlLoading(WebView view, String url) {
            return handleUrl(Uri.parse(url));
        }

        private boolean handleUrl(Uri uri) {
            String scheme = uri.getScheme() == null ? "" : uri.getScheme();
            if (scheme.startsWith("http") || scheme.equals("file")) {
                return false; // tetap di dalam WebView
            }
            // intent://, mailto:, whatsapp:// dll -> serahkan ke sistem
            try {
                startActivity(new Intent(Intent.ACTION_VIEW, uri));
            } catch (Exception ignored) {
            }
            return true;
        }

        @Override
        public boolean onRenderProcessGone(WebView view, android.webkit.RenderProcessGoneDetail detail) {
            // Renderer Chromium mati (biasanya kehabisan memori saat login TikTok).
            // Tangani sendiri supaya aplikasi tidak ikut tertutup.
            try {
                if (view == tiktokWeb) {
                    closeTikTok();
                    Toast.makeText(WebAppActivity.this,
                            "Halaman TikTok ditutup karena memori penuh. Coba lagi.",
                            Toast.LENGTH_LONG).show();
                } else if (view == web) {
                    root.removeView(view);
                    view.destroy();
                    web = newWebView();
                    root.addView(web, 0, new FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
                    web.loadUrl("file:///android_asset/web/index.html");
                    Toast.makeText(WebAppActivity.this,
                            "Memuat ulang aplikasi…", Toast.LENGTH_SHORT).show();
                }
            } catch (Exception ignored) {
            }
            return true; // penting: jangan biarkan sistem mematikan proses kita
        }

        @Override
        public void onPageFinished(WebView view, String url) {
            super.onPageFinished(view, url);
            if (view == web && url != null) {
                web.evaluateJavascript(
                        "window.Unrepost && window.Unrepost.onNavigate(" + jsString(url) + ");", null);
            }
        }
    }

    private class MyChromeClient extends WebChromeClient {
        @Override
        public void onPermissionRequest(final android.webkit.PermissionRequest request) {
            // situs kami tidak meminta kamera/mikrofon; tolak dengan sopan
            request.deny();
        }
    }

    // ------------------------------------------------------- DomRunner.Host

    @Override
    public void runJs(final String script, final ValueCallback<String> callback) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                WebView target = tiktokWeb != null ? tiktokWeb : web;
                if (target == null) {
                    if (callback != null) {
                        callback.onReceiveValue("err:no-webview");
                    }
                    return;
                }
                try {
                    target.evaluateJavascript(script == null ? "null" : script, callback);
                } catch (Exception e) {
                    if (callback != null) {
                        callback.onReceiveValue("err:" + e.getClass().getSimpleName());
                    }
                }
            }
        });
    }

    @Override
    public void openUrl(final String url) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (tiktokWeb == null) {
                    openTikTok(url);
                } else {
                    tiktokWeb.loadUrl(url);
                }
            }
        });
    }

    @Override
    public boolean isLoading() {
        try {
            return tiktokWeb != null && tiktokWeb.getProgress() < 100;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public void onProgress(final String text, final String note, final int deleted,
                           final boolean finished, final boolean aborted) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                js2("window.UnrepostDomProgress && window.UnrepostDomProgress("
                        + jsString(text) + "," + jsString(note == null ? "" : note) + ","
                        + deleted + "," + finished + "," + aborted + ");");
            }
        });
    }

    @Override
    public void onLog(final String line) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                js2("window.UnrepostDomLog && window.UnrepostDomLog(" + jsString(line) + ");");
            }
        });
    }

    /** Jalankan JS di WebView utama (UI situs Unrepost), aman bila sudah hilang. */
    private void js2(String script) {
        try {
            if (web != null) {
                web.evaluateJavascript(script, null);
            }
        } catch (Exception ignored) {
        }
    }

    private void toastUi(final String msg) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                try {
                    Toast.makeText(WebAppActivity.this, msg, Toast.LENGTH_SHORT).show();
                } catch (Exception ignored) {
                }
            }
        });
    }

    // ---------------------------------------------------------------- bridge

    /** Jembatan minimal untuk situs lokal (tidak mengekspos apa pun yang sensitif). */
    public static class Bridge {

        private final WebAppActivity activity;

        Bridge(WebAppActivity activity) {
            this.activity = activity;
        }

        @JavascriptInterface
        public void openTikTok(final String url) {
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    activity.openTikTok(url);
                }
            });
        }

        @JavascriptInterface
        public void closeTikTok() {
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    activity.closeTikTok();
                }
            });
        }

        @JavascriptInterface
        public void notify(final String title, final String body) {
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(activity, title + ": " + body, Toast.LENGTH_LONG).show();
                }
            });
        }

        /** Mulai otomasi "hapus repost" (DOM) di WebView TikTok. */
        @JavascriptInterface
        public boolean startDomClean(final int maxDeletes, final int gapMs,
                                     final boolean dryRun, final String username) {
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    try {
                        if (activity.domRunner != null) {
                            activity.domRunner.stop();
                        }
                        activity.domRunner = new DomRunner(activity, activity, maxDeletes,
                                gapMs, dryRun, username);
                        if (activity.tiktokWeb != null) {
                            activity.tiktokWeb.addJavascriptInterface(
                                    activity.domRunner.new Bridge(), "UnrepostDom");
                        }
                        activity.openTikTok("https://www.tiktok.com/@" + username
                                + "?lang=id");
                        activity.domRunner.start();
                    } catch (Exception e) {
                        activity.toastUi("Gagal memulai: " + e.getClass().getSimpleName());
                    }
                }
            });
            return true;
        }

        @JavascriptInterface
        public void stopDomClean() {
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (activity.domRunner != null) {
                        activity.domRunner.stop();
                    }
                }
            });
        }

        /** Apakah cookie sesi TikTok sudah ada (sudah login). */
        @JavascriptInterface
        public boolean hasTiktokSession() {
            return DomRunner.hasSession(activity);
        }

        /** Nama akun TikTok yang login (dari cookie / halaman profil). Boleh kosong. */
        @JavascriptInterface
        public String tiktokUsername() {
            return DomRunner.readUsername(activity);
        }

        /** Probe: apakah snapshoter DOM terpasang di halaman TikTok aktif. */
        @JavascriptInterface
        public void probeDom() {
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (activity.domRunner == null) {
                        activity.domRunner = new DomRunner(activity, activity, 10, 12000L, true, "");
                    }
                    activity.domRunner.probe(new ValueCallback<String>() {
                        @Override
                        public void onReceiveValue(String value) {
                            activity.js2("window.UnrepostDomProbe && window.UnrepostDomProbe("
                                    + jsString(value == null ? "null" : value) + ");");
                        }
                    });
                }
            });
        }

        @JavascriptInterface
        public void clearTiktokSession() {
            DomRunner.clearSession();
        }

        @JavascriptInterface
        public void openAccessibilityCleaner() {
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    activity.startActivity(new Intent(activity, CleanerActivity.class));
                }
            });
        }

        @JavascriptInterface
        public void vibrate(int ms) {
            try {
                Vibrator v = (Vibrator) activity.getSystemService(VIBRATOR_SERVICE);
                if (v != null && v.hasVibrator()) {
                    v.vibrate(Math.max(10, Math.min(400, ms)));
                }
            } catch (Exception ignored) {
            }
        }

        @JavascriptInterface
        public void keepAwake(final boolean on) {
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (on) {
                        activity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                    } else {
                        activity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                    }
                }
            });
        }

        /**
         * Panggilan HTTP dari situs lokal ke TikTok.
         *
         * <p>Dilakukan native (bukan fetch di WebView) karena dua alasan:
         * TikTok tidak mengirim header CORS, dan POST tidak bisa di-proxy lewat
         * shouldInterceptRequest (body tidak tersedia). Cookie sesi diambil dari
         * CookieManager, jadi login lewat WebView TikTok otomatis ikut terpakai.</p>
         */
        @JavascriptInterface
        public void api(final String method, final String url, final String body,
                        final String callbackId) {
            // JANGAN memakai evaluateJavascript dari thread latar (itu bisa mematikan proses).
            // Hasil dikembalikan ke thread UI lewat Handler.
            new Thread(new Runnable() {
                @Override
                public void run() {
                    final String payload = Http.jsonRequest(method, url, body);
                    activity.main.post(new Runnable() {
                        @Override
                        public void run() {
                            activity.deliverCallback(callbackId, payload);
                        }
                    });
                }
            }, "unrepost-http").start();
        }

        @JavascriptInterface
        public String cookies(String url) {
            try {
                String c = CookieManager.getInstance().getCookie(url);
                return c == null ? "" : c;
            } catch (Exception e) {
                return "";
            }
        }

        @JavascriptInterface
        public String version() {
            return "2.2";
        }

        /** Info perangkat untuk layar Pengaturan (diagnostik bila ada masalah). */
        @JavascriptInterface
        public String deviceInfo() {
            try {
                android.os.Build b = new android.os.Build();
                return "{\"android\":" + android.os.Build.VERSION.SDK_INT
                        + ",\"model\":\"" + (android.os.Build.MANUFACTURER + " " + android.os.Build.MODEL)
                        .replace("\"", "") + "\",\"webview\":\""
                        + android.webkit.WebSettings.getDefaultUserAgent(activity)
                        .replaceAll("[^A-Za-z0-9 ./;-]", "").replace("\"", "")
                        + "\",\"heapMB\":" + (Runtime.getRuntime().maxMemory() / (1024 * 1024))
                        + "}";
            } catch (Exception e) {
                return "{}";
            }
        }
    }

    /** Klien HTTP minimal untuk jembatan JS. */
    static final class Http {

        private Http() {
        }

        static String jsonRequest(String method, String url, String body) {
            java.net.HttpURLConnection conn = null;
            try {
                conn = (java.net.HttpURLConnection) new java.net.URL(url).openConnection();
                conn.setRequestMethod(method == null ? "GET" : method.toUpperCase(java.util.Locale.ROOT));
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(20000);
                conn.setInstanceFollowRedirects(true);
                conn.setRequestProperty("Accept", "application/json, text/plain, */*");
                conn.setRequestProperty("Accept-Language", "id-ID,id;q=0.9,en;q=0.8");
                conn.setRequestProperty("Origin", "https://www.tiktok.com");
                conn.setRequestProperty("Referer", "https://www.tiktok.com/");
                conn.setRequestProperty("User-Agent",
                        "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) "
                                + "Version/4.0 Chrome/120.0 Mobile Safari/537.36");
                String cookie = CookieManager.getInstance().getCookie(url);
                if (cookie != null && !cookie.isEmpty()) {
                    conn.setRequestProperty("Cookie", cookie);
                }
                if (body != null && !body.isEmpty()) {
                    conn.setDoOutput(true);
                    conn.setRequestProperty("Content-Type",
                            "application/x-www-form-urlencoded; charset=UTF-8");
                    java.io.OutputStream os = conn.getOutputStream();
                    os.write(body.getBytes("UTF-8"));
                    os.flush();
                    os.close();
                }
                int code = conn.getResponseCode();
                java.io.InputStream is = code >= 400 ? conn.getErrorStream() : conn.getInputStream();
                String text = read(is);
                rememberCookies(url, conn);
                return "{\"status\":" + code + ",\"text\":" + quote(text) + "}";
            } catch (Exception e) {
                return "{\"status\":0,\"text\":\"\",\"error\":" + quote(String.valueOf(e.getMessage())) + "}";
            } finally {
                if (conn != null) {
                    conn.disconnect();
                }
            }
        }

        private static void rememberCookies(String url, java.net.HttpURLConnection conn) {
            try {
                for (java.util.Map.Entry<String, java.util.List<String>> e
                        : conn.getHeaderFields().entrySet()) {
                    if (e.getKey() == null || !e.getKey().equalsIgnoreCase("Set-Cookie")) {
                        continue;
                    }
                    for (String value : e.getValue()) {
                        CookieManager.getInstance().setCookie(url, value);
                    }
                }
            } catch (Exception ignored) {
            }
        }

        private static String read(java.io.InputStream is) throws java.io.IOException {
            if (is == null) {
                return "";
            }
            java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = is.read(buf)) > 0) {
                bos.write(buf, 0, n);
            }
            is.close();
            return new String(bos.toByteArray(), "UTF-8");
        }

        private static String quote(String s) {
            if (s == null) {
                return "null";
            }
            StringBuilder sb = new StringBuilder(s.length() + 16);
            sb.append('"');
            for (int i = 0; i < s.length(); i++) {
                char c = s.charAt(i);
                switch (c) {
                    case '"': sb.append("\\\""); break;
                    case '\\': sb.append("\\\\"); break;
                    case '\n': sb.append("\\n"); break;
                    case '\r': sb.append("\\r"); break;
                    case '\t': sb.append("\\t"); break;
                    default:
                        if (c < 0x20) {
                            sb.append(String.format("\\u%04x", (int) c));
                        } else {
                            sb.append(c);
                        }
                }
            }
            sb.append('"');
            return sb.toString();
        }
    }

    /**
     * Kirim hasil HTTP ke JavaScript. Dipanggil HANYA di thread UI dan selalu
     * dalam try/catch: bila WebView sudah hilang, aplikasi tidak boleh ikut mati.
     */
    void deliverCallback(String callbackId, String payload) {
        if (web == null || callbackId == null || payload == null) {
            return;
        }
        try {
            String js = "window.UnrepostNativeResult && window.UnrepostNativeResult("
                    + jsString(callbackId) + ", " + jsString(payload) + ");";
            web.evaluateJavascript(js, null);
        } catch (Exception ignored) {
            // WebView dimusnahkan saat proses berjalan — abaikan.
        }
    }

    private static String jsString(String s) {
        return "'" + s.replace("\\", "\\\\").replace("'", "\\'") + "'";
    }
}
