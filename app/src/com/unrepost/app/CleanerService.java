package com.unrepost.app;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Layanan aksesibilitas untuk "Bersihkan Repost": menghapus repost di profil TikTok
 * sendiri secara bertahap dengan jeda antar video.
 *
 * <p>Alur tiap video: buka video → menu bagikan → "Hapus repost" → konfirmasi →
 * jeda sesuai setelan → video berikutnya. Berhenti otomatis saat batas tercapai,
 * grid kosong, atau pengguna menekan Berhenti.</p>
 *
 * <p>Keselamatan: hanya berjalan di profil pengguna sendiri (tab "Repost" di
 * Profil/Saya), tidak pernah menyentuh feed For You, dan mendukung mode latihan.</p>
 */
public class CleanerService extends AccessibilityService {

    public static final String ACTION_START = "com.unrepost.app.CLEANER_START";
    public static final String ACTION_STOP = "com.unrepost.app.CLEANER_STOP";
    public static final String EXTRA_MAX = "max_deletes";
    public static final String EXTRA_GAP = "gap_ms";
    public static final String EXTRA_DRY = "dry_run";

    private static final String[] TIKTOK = {
            "com.zhiliaoapp.musically", "com.ss.android.ugc.trill",
            "com.ss.android.ugc.aweme", "com.tiktok.lite", "com.tiktok.lite.go"
    };

    private static final long TICK_MS = 650L;

    private static volatile CleanerService instance;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private CleanerEngine engine;
    private boolean running;
    private boolean busy;

    private WindowManager wm;
    private TextView bubble;
    private boolean bubbleAdded;
    private long lastBubbleUpdate;

    public static CleanerService get() {
        return instance;
    }

    public static boolean isRunning() {
        CleanerService s = instance;
        return s != null && s.running;
    }

    public static String progress() {
        CleanerService s = instance;
        if (s == null || s.engine == null) {
            return "";
        }
        return s.engine.progressText();
    }

    public static boolean startCleaning(int maxDeletes, int gapMs, boolean dryRun) {
        CleanerService s = instance;
        if (s == null) {
            return false;
        }
        s.start(maxDeletes, gapMs, dryRun);
        return true;
    }

    public static void stopCleaning() {
        CleanerService s = instance;
        if (s != null) {
            s.stop();
        }
    }

    @Override
    public void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
    }

    @Override
    public boolean onUnbind(android.content.Intent intent) {
        instance = null;
        cancel();
        return super.onUnbind(intent);
    }

    @Override
    public void onDestroy() {
        instance = null;
        cancel();
        super.onDestroy();
    }

    @Override
    public void onInterrupt() {
        cancel();
    }

    @Override
    public void onAccessibilityEvent(android.view.accessibility.AccessibilityEvent event) {
        // Mesin bekerja berdasarkan polling; event hanya memicu tick lebih cepat.
        if (running && !busy && event != null && isTikTok(event.getPackageName())) {
            tickSoon(120L);
        }
    }

    // ------------------------------------------------------------------ start

    private void start(int maxDeletes, int gapMs, boolean dryRun) {
        engine = new CleanerEngine(maxDeletes, gapMs, dryRun);
        running = true;
        busy = false;
        CleanerState.markStarting(this, maxDeletes, gapMs, dryRun);
        showBubble();
        toast(dryRun ? "Mode latihan dimulai" : "Pembersihan repost dimulai");
        tickSoon(300L);
    }

    private void stop() {
        if (engine != null) {
            engine.stop();
        }
        running = false;
        handler.removeCallbacks(tickRunnable);
        removeBubble();
        toast("Pembersihan dihentikan");
        CleanerState.finish(this, engine, false);
    }

    private void cancel() {
        running = false;
        handler.removeCallbacks(tickRunnable);
        removeBubble();
    }

    // ------------------------------------------------------------------ loop

    private void tickSoon(long delay) {
        handler.removeCallbacks(tickRunnable);
        handler.postDelayed(tickRunnable, delay);
    }

    private final Runnable tickRunnable = new Runnable() {
        @Override
        public void run() {
            if (!running || engine == null) {
                return;
            }
            if (busy) {
                tickSoon(TICK_MS);
                return;
            }
            busy = true;
            try {
                tick();
            } catch (Exception ignored) {
                // layanan aksesibilitas tidak boleh crash
            } finally {
                busy = false;
                if (running) {
                    tickSoon(TICK_MS);
                }
            }
        }
    };

    private void tick() {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        List<String> labels = new ArrayList<>();
        boolean dialogOpen = false;
        if (root != null) {
            boolean[] flags = new boolean[]{false};
            collect(root, labels, 0, new int[]{0}, flags);
            dialogOpen = flags[0] || hasDialogWindow();
        }
        CleanerEngine.Decision d = engine.onTick(labels, dialogOpen, System.currentTimeMillis());
        updateBubble();

        switch (d.action) {
            case TAP_PROFILE:
            case TAP_REPOST_TAB:
            case TAP_MENU:
            case TAP_REMOVE:
            case TAP_CONFIRM:
                tapLabel(root, d.label);
                break;
            case TAP_FIRST_ITEM:
                tapFirstGridItem(root);
                break;
            case FINISH:
                finish(false);
                break;
            case ABORT:
                finish(true);
                break;
            default:
                break;
        }
    }

    private void finish(boolean aborted) {
        running = false;
        handler.removeCallbacks(tickRunnable);
        removeBubble();
        if (aborted) {
            toast("Berhenti: layar tidak dikenali. Buka Profil \u2192 Repost lalu coba lagi.");
        } else if (engine != null && engine.deleted() > 0) {
            toast((engine.isDryRun() ? "Latihan selesai: " : "Selesai: ")
                    + engine.deleted() + " repost diproses");
        } else {
            toast("Selesai: tidak ada repost untuk diproses");
        }
        CleanerState.finish(this, engine, aborted);
    }

    // ------------------------------------------------------------------ aktor

    /** Ketuk node yang teks/deskripsinya cocok dengan label. */
    private void tapLabel(AccessibilityNodeInfo root, String label) {
        if (root == null || label == null) {
            return;
        }
        AccessibilityNodeInfo node = findNode(root, label.toLowerCase(Locale.ROOT), 0, new int[]{0});
        if (node == null) {
            return;
        }
        // naik ke leluhur yang clickable
        AccessibilityNodeInfo clickable = node;
        for (int i = 0; i < 6 && clickable != null; i++) {
            if (clickable.isClickable()) {
                break;
            }
            clickable = clickable.getParent();
        }
        if (clickable != null && clickable.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
            return;
        }
        // fallback: ketuk koordinat tengah elemen
        android.graphics.Rect r = new android.graphics.Rect();
        node.getBoundsInScreen(r);
        if (r.width() > 0 && r.height() > 0) {
            tap(r.centerX(), r.centerY());
        }
    }

    /** Ketuk ubin pertama di grid repost (node terbesar yang clickable di area grid). */
    private void tapFirstGridItem(AccessibilityNodeInfo root) {
        if (root == null) {
            return;
        }
        DisplayMetrics dm = getResources().getDisplayMetrics();
        int minY = (int) (dm.heightPixels * 0.28f);
        int maxY = (int) (dm.heightPixels * 0.92f);
        AccessibilityNodeInfo best = null;
        android.graphics.Rect bestRect = null;
        List<AccessibilityNodeInfo> stack = new ArrayList<>();
        stack.add(root);
        int guard = 0;
        while (!stack.isEmpty() && guard++ < 1200) {
            AccessibilityNodeInfo n = stack.remove(stack.size() - 1);
            if (n == null) {
                continue;
            }
            android.graphics.Rect r = new android.graphics.Rect();
            n.getBoundsInScreen(r);
            if (n.isClickable() && r.centerY() > minY && r.centerY() < maxY
                    && r.width() > dm.widthPixels * 0.2f && r.height() > dm.heightPixels * 0.1f) {
                if (bestRect == null || (r.centerX() < bestRect.centerX()
                        && Math.abs(r.centerY() - bestRect.centerY()) < r.height())
                        || r.centerY() < bestRect.centerY()) {
                    best = n;
                    bestRect = new android.graphics.Rect(r);
                }
            }
            int count = n.getChildCount();
            for (int i = 0; i < count; i++) {
                try {
                    stack.add(n.getChild(i));
                } catch (Exception ignored) {
                }
            }
        }
        if (bestRect != null) {
            // pilih ubin paling kiri-atas
            tap(bestRect.centerX(), bestRect.centerY());
        } else {
            tap(dm.widthPixels * 0.17f, dm.heightPixels * 0.45f);
        }
    }

    private void tap(float x, float y) {
        try {
            Path p = new Path();
            p.moveTo(x, y);
            GestureDescription g = new GestureDescription.Builder()
                    .addStroke(new GestureDescription.StrokeDescription(p, 0, 60L))
                    .build();
            dispatchGesture(g, null, null);
        } catch (Exception ignored) {
        }
    }

    // ------------------------------------------------------------- node utils

    /** true bila ada lebih dari satu jendela TikTok (mis. sheet bagikan di atas profil). */
    private boolean hasDialogWindow() {
        try {
            int tiktokWindows = 0;
            for (android.view.accessibility.AccessibilityWindowInfo w : getWindows()) {
                if (w.getType() != android.view.accessibility.AccessibilityWindowInfo.TYPE_APPLICATION) {
                    continue;
                }
                AccessibilityNodeInfo r = w.getRoot();
                if (r == null) {
                    continue;
                }
                CharSequence pkg = r.getPackageName();
                if (pkg != null && isTikTok(pkg)) {
                    tiktokWindows++;
                }
            }
            return tiktokWindows > 1;
        } catch (Exception e) {
            return false;
        }
    }

    private AccessibilityNodeInfo findNode(AccessibilityNodeInfo node, String label,
                                           int depth, int[] budget) {
        if (node == null || depth > 40 || budget[0] > 1200) {
            return null;
        }
        budget[0]++;
        CharSequence text = node.getText();
        if (text != null && text.toString().toLowerCase(Locale.ROOT).trim().equals(label)) {
            return node;
        }
        CharSequence desc = node.getContentDescription();
        if (desc != null && desc.toString().toLowerCase(Locale.ROOT).trim().equals(label)) {
            return node;
        }
        int count = node.getChildCount();
        for (int i = 0; i < count; i++) {
            AccessibilityNodeInfo child;
            try {
                child = node.getChild(i);
            } catch (Exception e) {
                continue;
            }
            AccessibilityNodeInfo found = findNode(child, label, depth + 1, budget);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private void collect(AccessibilityNodeInfo node, List<String> out, int depth,
                         int[] budget, boolean[] dialogFlag) {
        if (node == null || depth > 40 || budget[0] > 1000) {
            return;
        }
        budget[0]++;
        CharSequence text = node.getText();
        if (text != null) {
            String s = text.toString().trim();
            if (s.length() > 0 && s.length() < 120) {
                out.add(s);
            }
        }
        CharSequence desc = node.getContentDescription();
        if (desc != null) {
            String s = desc.toString().trim();
            if (s.length() > 0 && s.length() < 120) {
                out.add(s);
            }
        }
        if (node.getClassName() != null) {
            String cls = node.getClassName().toString();
            if (cls.contains("Dialog") || cls.contains("PopupWindow")
                    || cls.contains("ModalBottomSheet") || cls.contains("BottomSheet")) {
                dialogFlag[0] = true;
            }
        }
        int count = node.getChildCount();
        for (int i = 0; i < count; i++) {
            try {
                collect(node.getChild(i), out, depth + 1, budget, dialogFlag);
            } catch (Exception ignored) {
            }
        }
    }

    private boolean isTikTok(CharSequence pkg) {
        if (pkg == null) {
            return false;
        }
        String p = pkg.toString().toLowerCase(Locale.ROOT);
        for (String t : TIKTOK) {
            if (p.equals(t)) {
                return true;
            }
        }
        return p.contains("tiktok") || p.contains("musically") || p.contains("trill");
    }

    // ---------------------------------------------------------------- overlay

    private void showBubble() {
        if (wm == null) {
            return;
        }
        try {
            if (bubble == null) {
                bubble = new TextView(this);
                bubble.setTextColor(0xFFFFFFFF);
                bubble.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f);
                bubble.setPadding(dp(14), dp(9), dp(14), dp(9));
                GradientDrawable bg = new GradientDrawable();
                bg.setColor(0xF015151C);
                bg.setCornerRadius(dp(14));
                bg.setStroke(dp(1), 0xFFFE2C55);
                bubble.setBackground(bg);
            }
            bubble.setText(engine == null ? "Bersihkan repost" : engine.progressText());
            if (!bubbleAdded) {
                WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                        WindowManager.LayoutParams.WRAP_CONTENT,
                        WindowManager.LayoutParams.WRAP_CONTENT,
                        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                                | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                                | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                        PixelFormat.TRANSLUCENT);
                lp.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
                lp.y = dp(56);
                wm.addView(bubble, lp);
                bubbleAdded = true;
            }
        } catch (Exception ignored) {
        }
    }

    private void updateBubble() {
        long now = System.currentTimeMillis();
        if (now - lastBubbleUpdate < 500L || bubble == null || engine == null) {
            return;
        }
        lastBubbleUpdate = now;
        try {
            bubble.setText(engine.progressText() + "\n" + engine.lastNote());
            CleanerState.updateProgress(this, engine.deleted(), engine.progressText());
        } catch (Exception ignored) {
        }
    }

    private void removeBubble() {
        try {
            if (bubbleAdded && wm != null && bubble != null) {
                wm.removeView(bubble);
            }
        } catch (Exception ignored) {
        } finally {
            bubbleAdded = false;
        }
    }

    private void toast(String msg) {
        try {
            android.widget.Toast.makeText(this, msg, android.widget.Toast.LENGTH_SHORT).show();
        } catch (Exception ignored) {
        }
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
