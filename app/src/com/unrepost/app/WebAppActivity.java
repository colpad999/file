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
public class WebAppActivity extends Activity {

    private WebView web;
    private FrameLayout root;
    private WebView tiktokWeb;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        root = new FrameLayout(this);
        root.setBackgroundColor(Color.parseColor("#0B0B0F"));

        web = new WebView(this);
        WebSettings s = web.getSettings();
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
            cm.setAcceptThirdPartyCookies(web, true);
        }

        web.setBackgroundColor(Color.parseColor("#0B0B0F"));
        web.setWebChromeClient(new MyChromeClient());
        web.setWebViewClient(new MyWebClient());
        web.addJavascriptInterface(new Bridge(this), "UnrepostNative");

        root.addView(web, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        setContentView(root);

        web.loadUrl("file:///android_asset/web/index.html");
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
        if (tiktokWeb == null) {
            tiktokWeb = new WebView(this);
            WebSettings s = tiktokWeb.getSettings();
            s.setJavaScriptEnabled(true);
            s.setDomStorageEnabled(true);
            s.setDatabaseEnabled(true);
            s.setLoadWithOverviewMode(true);
            s.setUseWideViewPort(true);
            s.setSupportZoom(true);
            s.setBuiltInZoomControls(true);
            s.setDisplayZoomControls(false);
            s.setJavaScriptCanOpenWindowsAutomatically(true);
            s.setSupportMultipleWindows(false);
            s.setUserAgentString(s.getUserAgentString().replace("; wv)", ")"));
            CookieManager cm = CookieManager.getInstance();
            cm.setAcceptCookie(true);
            if (Build.VERSION.SDK_INT >= 21) {
                cm.setAcceptThirdPartyCookies(tiktokWeb, true);
            }
            tiktokWeb.setBackgroundColor(Color.parseColor("#000000"));
            tiktokWeb.setWebChromeClient(new MyChromeClient());
            tiktokWeb.setWebViewClient(new WebViewClient() {
                @Override
                public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                    return false;
                }

                @Override
                public void onPageFinished(WebView view, String url) {
                    super.onPageFinished(view, url);
                    CookieManager.getInstance().flush();
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
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            String payload = Http.jsonRequest(method, url, body);
                            activity.web.evaluateJavascript(
                                    "window.UnrepostNativeResult && window.UnrepostNativeResult("
                                            + jsString(callbackId) + ", " + jsString(payload) + ");",
                                    null);
                        }
                    }, "unrepost-http").start();
                }
            });
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
            return "2.1";
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

    private static String jsString(String s) {
        return "'" + s.replace("\\", "\\\\").replace("'", "\\'") + "'";
    }
}
