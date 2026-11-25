package com.unrepost.app;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.graphics.Color;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Layanan aksesibilitas yang memantau layar TikTok, mendeteksi video repost,
 * menandainya lewat badge, dan (opsional) melewatinya otomatis.
 */
public class UnrepostService extends AccessibilityService {

    /** Paket TikTok: versi global (musically), versi Asia (trill), dan TikTok Lite. */
    private static final String[] TIKTOK_PACKAGES = {
            "com.zhiliaoapp.musically",
            "com.ss.android.ugc.trill",
            "com.ss.android.ugc.aweme",
            "com.tiktok.lite",
            "com.tiktok.lite.go"
    };

    /** Label yang menunjukkan ada sheet komentar / dialog terbuka — jangan geser layar. */
    private static final String[] BLOCKING_UI = {
            "tambahkan komentar", "add comment", "add a comment", "balas", "reply",
            "komentar (", "comments (", "bagikan ke", "share to", "salin tautan",
            "copy link", "laporkan", "report", "simpan video", "save video",
            "hapus", "delete", "batal", "cancel", "pilih", "select"
    };

    private static final long THROTTLE_MS = 700L;
    private static final long MIN_GESTURE_GAP_MS = 3500L;
    private static final long SAME_SCREEN_SUPPRESS_MS = 8000L;
    private static final long BADGE_MS = 2600L;

    private static volatile UnrepostService instance;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Set<String> handledSignatures = new HashSet<>();

    private long lastRunAt;
    private long lastGestureAt;
    private String lastSignature = "";
    private long lastSignatureAt;
    private boolean busy;

    private WindowManager windowManager;
    private TextView badge;
    private boolean badgeAdded;

    public static UnrepostService get() {
        return instance;
    }

    public static boolean isRunning() {
        return instance != null;
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        // Aktifkan penyaring otomatis saat izin aksesibilitas baru diberikan.
        android.content.SharedPreferences sp = Prefs.get(this);
        if (!sp.contains(Prefs.KEY_ENABLED)) {
            sp.edit().putBoolean(Prefs.KEY_ENABLED, true).apply();
        }
    }

    @Override
    public boolean onUnbind(android.content.Intent intent) {
        instance = null;
        removeBadge();
        return super.onUnbind(intent);
    }

    @Override
    public void onDestroy() {
        instance = null;
        removeBadge();
        super.onDestroy();
    }

    @Override
    public void onInterrupt() {
        // tidak ada ucapan; layanan ini tidak berbicara
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null || !Prefs.filterEnabled(this)) {
            return;
        }
        if (!isTikTok(event.getPackageName())) {
            return;
        }
        int type = event.getEventType();
        if (type != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                && type != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
                && type != AccessibilityEvent.TYPE_VIEW_SCROLLED) {
            return;
        }
        long now = System.currentTimeMillis();
        if (busy || now - lastRunAt < THROTTLE_MS) {
            return;
        }
        lastRunAt = now;
        handler.postDelayed(this::scan, 180L);
    }

    private void scan() {
        if (!Prefs.filterEnabled(this)) {
            return;
        }
        AccessibilityNodeInfo root;
        try {
            root = getRootInActiveWindow();
        } catch (Exception e) {
            return;
        }
        if (root == null) {
            return;
        }
        busy = true;
        try {
            List<String> lines = new ArrayList<>();
            collect(root, lines, 0, new int[]{0});
            if (lines.isEmpty()) {
                return;
            }
            String joined = join(lines);
            RepostDetector.Result result =
                    RepostDetector.analyze(joined, Prefs.threshold(this));
            if (!result.isRepost || result.signature.isEmpty()) {
                return;
            }
            long now = System.currentTimeMillis();
            boolean sameScreen = result.signature.equals(lastSignature)
                    && now - lastSignatureAt < SAME_SCREEN_SUPPRESS_MS;
            if (sameScreen) {
                return;
            }
            lastSignature = result.signature;
            lastSignatureAt = now;

            Prefs.noteDetected(this, result.signature, result.title);
            showBadge(result);

            if (Prefs.autoSkip(this) && !hasBlockingUi(lines)) {
                maybeSwipe();
            }
        } catch (Exception ignored) {
            // Layanan aksesibilitas tidak boleh crash karena satu layar aneh.
        } finally {
            busy = false;
        }
    }

    private void maybeSwipe() {
        long now = System.currentTimeMillis();
        if (now - lastGestureAt < MIN_GESTURE_GAP_MS) {
            return;
        }
        lastGestureAt = now;
        handler.postDelayed(() -> {
            try {
                DisplayMetrics dm = getResources().getDisplayMetrics();
                float x = dm.widthPixels / 2f;
                float fromY = dm.heightPixels * 0.78f;
                float toY = dm.heightPixels * 0.18f;
                Path path = new Path();
                path.moveTo(x, fromY);
                path.lineTo(x, toY);
                GestureDescription gesture = new GestureDescription.Builder()
                        .addStroke(new GestureDescription.StrokeDescription(path, 0, 260L))
                        .build();
                Prefs.noteSkipped(this);
                dispatchGesture(gesture, null, null);
            } catch (Exception ignored) {
                // perangkat tanpa dukungan gesture
            }
        }, 400L);
    }

    // ---------------------------------------------------------------- overlay

    private void showBadge(RepostDetector.Result result) {
        if (windowManager == null) {
            return;
        }
        try {
            if (badge == null) {
                badge = new TextView(this);
                badge.setTextColor(Color.WHITE);
                badge.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f);
                badge.setPadding(dp(14), dp(9), dp(14), dp(9));
                badge.setGravity(Gravity.CENTER);
                badge.setElevation(dp(6));
                GradientDrawable bg = new GradientDrawable();
                bg.setColor(0xE615151C);
                bg.setCornerRadius(dp(14));
                bg.setStroke(dp(1), 0xFFFE2C55);
                badge.setBackground(bg);
            }
            badge.setText("\u26D4  Repost terdeteksi \u00b7 skor " + result.score);

            if (!badgeAdded) {
                WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                        WindowManager.LayoutParams.WRAP_CONTENT,
                        WindowManager.LayoutParams.WRAP_CONTENT,
                        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                                | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                                | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                        PixelFormat.TRANSLUCENT);
                lp.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
                lp.y = dp(72);
                windowManager.addView(badge, lp);
                badgeAdded = true;
            }
            badge.setVisibility(View.VISIBLE);
            handler.removeCallbacks(hideBadge);
            handler.postDelayed(hideBadge, BADGE_MS);
        } catch (Exception ignored) {
            // overlay tidak tersedia
        }
    }

    private final Runnable hideBadge = () -> {
        try {
            if (badge != null) {
                badge.setVisibility(View.GONE);
            }
        } catch (Exception ignored) {
        }
    };

    private void removeBadge() {
        try {
            if (badgeAdded && windowManager != null && badge != null) {
                windowManager.removeView(badge);
            }
        } catch (Exception ignored) {
        } finally {
            badgeAdded = false;
        }
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    // ---------------------------------------------------------------- helpers

    private boolean isTikTok(CharSequence pkg) {
        if (pkg == null) {
            return false;
        }
        String p = pkg.toString().toLowerCase(Locale.ROOT);
        for (String t : TIKTOK_PACKAGES) {
            if (p.equals(t)) {
                return true;
            }
        }
        return p.contains("tiktok") || p.contains("musically") || p.contains("trill");
    }

    private boolean hasBlockingUi(List<String> lines) {
        for (String line : lines) {
            String l = line.toLowerCase(Locale.ROOT);
            for (String b : BLOCKING_UI) {
                if (l.contains(b)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Kumpulkan teks yang terlihat, sekali per baris, maksimal 900 node. */
    private void collect(AccessibilityNodeInfo node, List<String> out, int depth, int[] budget) {
        if (node == null || depth > 45 || budget[0] > 900) {
            return;
        }
        budget[0]++;
        CharSequence text = node.getText();
        if (text != null) {
            String s = text.toString().trim();
            if (s.length() > 1 && s.length() < 900) {
                out.add(s);
            }
        }
        CharSequence desc = node.getContentDescription();
        if (desc != null) {
            String s = desc.toString().trim();
            if (s.length() > 2 && s.length() < 300) {
                out.add(s);
            }
        }
        int count;
        try {
            count = node.getChildCount();
        } catch (Exception e) {
            return;
        }
        for (int i = 0; i < count; i++) {
            AccessibilityNodeInfo child;
            try {
                child = node.getChild(i);
            } catch (Exception e) {
                continue;
            }
            if (child != null) {
                collect(child, out, depth + 1, budget);
            }
        }
    }

    private String join(List<String> lines) {
        Set<String> unique = new HashSet<>();
        StringBuilder sb = new StringBuilder();
        for (String l : lines) {
            String compact = l.replaceAll("\\s+", " ");
            if (compact.length() < 2 || !unique.add(compact)) {
                continue;
            }
            if (RepostDetector.isChrome(compact)) {
                continue;
            }
            sb.append(compact).append('\n');
            if (sb.length() > 6000) {
                break;
            }
        }
        return sb.toString();
    }
}
