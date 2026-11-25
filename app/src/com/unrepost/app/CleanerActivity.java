package com.unrepost.app;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

/**
 * Layar "Bersihkan Repost": hapus repost di akun TikTok sendiri secara otomatis,
 * satu klik, bertahap (satu video per jeda).
 */
public class CleanerActivity extends Activity {

    private static final int C_BG = 0xFF0B0B0F;
    private static final int C_CARD = 0xFF15151C;
    private static final int C_STROKE = 0xFF26262F;
    private static final int C_CYAN = 0xFF25F4EE;
    private static final int C_PINK = 0xFFFE2C55;
    private static final int C_TEXT = 0xFFF4F4F6;
    private static final int C_DIM = 0xFF9A9AA6;
    private static final int C_OK = 0xFF34D399;

    private final Handler handler = new Handler(Looper.getMainLooper());

    private TextView progressText;
    private TextView logText;
    private Button startButton;
    private Button stopButton;
    private LinearLayout openTiktokRow;

    private int count = 25;
    private int gap = 8;
    private boolean dryRun = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(buildUi());
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
        handler.post(progressLoop);
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(progressLoop);
    }

    // --------------------------------------------------------------------- UI

    private View buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(C_BG);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(26), dp(20), dp(30));
        scroll.addView(root, matchWrap());

        // Header
        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);

        TextView logo = new TextView(this);
        logo.setText("\uD83D\uDDD1");
        logo.setTextSize(TypedValue.COMPLEX_UNIT_SP, 26f);
        head.addView(logo, wrapWrap());

        LinearLayout titleBox = new LinearLayout(this);
        titleBox.setOrientation(LinearLayout.VERTICAL);
        titleBox.setPadding(dp(12), 0, 0, 0);

        TextView title = new TextView(this);
        title.setText("Bersihkan Repost");
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 24f);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(C_TEXT);
        titleBox.addView(title, matchWrap());

        TextView sub = new TextView(this);
        sub.setText("Hapus repost dari profil TikTok Anda \u2014 otomatis, bertahap");
        sub.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f);
        sub.setTextColor(C_DIM);
        titleBox.addView(sub, matchWrap());
        head.addView(titleBox, matchWrap());
        root.addView(head, matchWrap());
        root.addView(space(18));

        // Status izin
        LinearLayout card = card();
        root.addView(card, matchWrap());
        card.addView(label("LANGKAH 1 \u00b7 IZIN"));
        TextView izin = new TextView(this);
        izin.setText("Aplikasi memakai izin Aksesibilitas untuk mengetuk menu TikTok "
                + "(\"Hapus repost\") atas nama Anda. Izin ini hanya bisa dinyalakan sendiri "
                + "oleh Anda \u2014 Android tidak mengizinkan aplikasi mana pun menyalakannya otomatis.");
        izin.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f);
        izin.setTextColor(C_DIM);
        izin.setLineSpacing(dp(3), 1f);
        izin.setPadding(0, dp(8), 0, dp(12));
        card.addView(izin, matchWrap());

        Button izinButton = new Button(this);
        izinButton.setText("Aktifkan aksesibilitas (1 ketukan)");
        izinButton.setAllCaps(false);
        izinButton.setTextColor(0xFF0B0B0F);
        izinButton.setTypeface(Typeface.DEFAULT_BOLD);
        izinButton.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f);
        izinButton.setBackground(rounded(C_CYAN, 14));
        izinButton.setPadding(dp(16), dp(12), dp(16), dp(12));
        izinButton.setOnClickListener(v -> openAccessibility());
        card.addView(izinButton, matchWrap());

        // Langkah 2: setelan
        root.addView(space(14));
        LinearLayout settings = card();
        root.addView(settings, matchWrap());
        settings.addView(label("LANGKAH 2 \u00b7 SETELAN"));

        final TextView countLabel = new TextView(this);
        countLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f);
        countLabel.setTextColor(C_TEXT);
        countLabel.setPadding(0, dp(10), 0, dp(4));
        countLabel.setText("Maksimal video: " + count);
        settings.addView(countLabel, matchWrap());

        SeekBar countBar = new SeekBar(this);
        countBar.setMax(99);
        countBar.setProgress(count - 1);
        countBar.setOnSeekBarChangeListener(new SimpleSeek() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                count = progress + 1;
                countLabel.setText("Maksimal video: " + count);
            }
        });
        settings.addView(countBar, matchWrap());

        final TextView gapLabel = new TextView(this);
        gapLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f);
        gapLabel.setTextColor(C_TEXT);
        gapLabel.setPadding(0, dp(14), 0, dp(4));
        gapLabel.setText("Jeda antar video: " + gap + " detik");
        settings.addView(gapLabel, matchWrap());

        SeekBar gapBar = new SeekBar(this);
        gapBar.setMax(37);
        gapBar.setProgress(gap - 3);
        gapBar.setOnSeekBarChangeListener(new SimpleSeek() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                gap = progress + 3;
                gapLabel.setText("Jeda antar video: " + gap + " detik"
                        + (gap >= 10 ? " (disarankan)" : " (cepat, risiko terdeteksi)"));
            }
        });
        settings.addView(gapBar, matchWrap());

        TextView gapHint = new TextView(this);
        gapHint.setText("Jeda 10\u201315 detik adalah rentang aman: TikTok tidak menandainya sebagai "
                + "aktivitas bot, dan Anda masih melihat setiap video yang dihapus.");
        gapHint.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f);
        gapHint.setTextColor(C_DIM);
        gapHint.setPadding(0, dp(8), 0, dp(10));
        settings.addView(gapHint, matchWrap());

        // Mode latihan
        LinearLayout dryRow = new LinearLayout(this);
        dryRow.setOrientation(LinearLayout.HORIZONTAL);
        dryRow.setGravity(Gravity.CENTER_VERTICAL);
        dryRow.setPadding(0, dp(6), 0, 0);
        final TextView dryStatus = new TextView(this);
        dryStatus.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f);
        dryStatus.setTextColor(C_DIM);
        dryStatus.setText("Mode latihan (tidak menghapus, hanya mencatat)");
        dryRow.addView(dryStatus, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        Button dryToggle = new Button(this);
        dryToggle.setText(dryRun ? "AKTIF" : "MATI");
        dryToggle.setAllCaps(false);
        dryToggle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f);
        dryToggle.setBackground(roundedStroke(C_CARD, dryRun ? C_OK : C_STROKE, 10));
        dryToggle.setTextColor(dryRun ? C_OK : C_DIM);
        dryToggle.setOnClickListener(v -> {
            dryRun = !dryRun;
            dryToggle.setText(dryRun ? "AKTIF" : "MATI");
            dryToggle.setTextColor(dryRun ? C_OK : C_DIM);
            dryToggle.setBackground(roundedStroke(C_CARD, dryRun ? C_OK : C_STROKE, 10));
            Toast.makeText(this, dryRun ? "Mode latihan: tidak ada yang dihapus"
                    : "Mode hapus aktif", Toast.LENGTH_SHORT).show();
        });
        dryRow.addView(dryToggle, wrapWrap());
        settings.addView(dryRow, matchWrap());

        // Langkah 3: jalankan
        root.addView(space(14));
        LinearLayout run = card();
        root.addView(run, matchWrap());
        run.addView(label("LANGKAH 3 \u00b7 JALANKAN"));

        TextView runHint = new TextView(this);
        runHint.setText("Tekan mulai \u2192 aplikasi membuka Profil \u2192 tab Repost \u2192 menghapus "
                + "satu video per jeda di layar TikTok. Jangan ketuk layar selama proses; "
                + "tekan Berhenti kapan saja.");
        runHint.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f);
        runHint.setTextColor(C_DIM);
        runHint.setLineSpacing(dp(3), 1f);
        runHint.setPadding(0, dp(8), 0, dp(12));
        run.addView(runHint, matchWrap());

        startButton = new Button(this);
        startButton.setAllCaps(false);
        startButton.setTypeface(Typeface.DEFAULT_BOLD);
        startButton.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f);
        startButton.setPadding(dp(16), dp(14), dp(16), dp(14));
        startButton.setOnClickListener(v -> onStartClicked());
        run.addView(startButton, matchWrap());

        stopButton = new Button(this);
        stopButton.setText("Berhenti");
        stopButton.setAllCaps(false);
        stopButton.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f);
        stopButton.setTextColor(C_TEXT);
        stopButton.setBackground(roundedStroke(C_CARD, C_PINK, 14));
        stopButton.setPadding(dp(16), dp(12), dp(16), dp(12));
        stopButton.setOnClickListener(v -> {
            CleanerService.stopCleaning();
            refresh();
        });
        LinearLayout.LayoutParams stopLp = matchWrap();
        stopLp.topMargin = dp(10);
        run.addView(stopButton, stopLp);

        openTiktokRow = new LinearLayout(this);
        openTiktokRow.setOrientation(LinearLayout.HORIZONTAL);
        openTiktokRow.setPadding(0, dp(10), 0, 0);
        Button openTiktok = new Button(this);
        openTiktok.setText("\u2192 Buka TikTok manual");
        openTiktok.setAllCaps(false);
        openTiktok.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f);
        openTiktok.setTextColor(C_CYAN);
        openTiktok.setBackground(roundedStroke(C_CARD, C_STROKE, 12));
        openTiktok.setOnClickListener(v -> openTikTok());
        openTiktokRow.addView(openTiktok, weightWrap(1));
        run.addView(openTiktokRow, matchWrap());

        // Progres
        root.addView(space(14));
        LinearLayout status = card();
        root.addView(status, matchWrap());
        status.addView(label("PROGRES"));

        progressText = new TextView(this);
        progressText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f);
        progressText.setTypeface(Typeface.DEFAULT_BOLD);
        progressText.setTextColor(C_TEXT);
        progressText.setPadding(0, dp(8), 0, dp(8));
        status.addView(progressText, matchWrap());

        logText = new TextView(this);
        logText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f);
        logText.setTextColor(C_DIM);
        logText.setLineSpacing(dp(3), 1f);
        status.addView(logText, matchWrap());

        TextView legal = new TextView(this);
        legal.setText("Hanya untuk akun Anda sendiri. Aplikasi tidak menyimpan sandi, tidak "
                + "mengirim data, dan berhenti otomatis bila layar tidak dikenali.");
        legal.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f);
        legal.setTextColor(C_DIM);
        legal.setPadding(0, dp(18), 0, 0);
        root.addView(legal, matchWrap());

        return scroll;
    }

    // ------------------------------------------------------------------- aksi

    private void onStartClicked() {
        if (!isAccessibilityEnabled()) {
            Toast.makeText(this, "Aktifkan aksesibilitas dulu (langkah 1)", Toast.LENGTH_LONG).show();
            openAccessibility();
            return;
        }
        if (CleanerState.isRunning(this)) {
            Toast.makeText(this, "Pembersihan sedang berjalan", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!CleanerService.isRunning()) {
            Toast.makeText(this, "Layanan belum siap \u2014 matikan & nyalakan ulang izin aksesibilitas",
                    Toast.LENGTH_LONG).show();
            return;
        }
        int gapMs = gap * 1000;
        CleanerState.markStarting(this, count, gapMs, dryRun);
        boolean ok = CleanerService.startCleaning(count, gapMs, dryRun);
        if (!ok) {
            Toast.makeText(this, "Gagal memulai layanan", Toast.LENGTH_SHORT).show();
            return;
        }
        // Buka TikTok langsung supaya proses bisa dimulai tanpa langkah tambahan.
        openTikTok();
        refresh();
    }

    private void openTikTok() {
        String[] pkgs = {"com.zhiliaoapp.musically", "com.ss.android.ugc.trill",
                "com.ss.android.ugc.aweme", "com.tiktok.lite"};
        for (String pkg : pkgs) {
            Intent i = getPackageManager().getLaunchIntentForPackage(pkg);
            if (i != null) {
                startActivity(i);
                return;
            }
        }
        Toast.makeText(this, "TikTok belum terpasang", Toast.LENGTH_LONG).show();
    }

    private void openAccessibility() {
        try {
            Intent i = new Intent("android.settings.ACCESSIBILITY_DETAILS_SETTINGS");
            i.setData(Uri.parse("package:" + getPackageName()));
            startActivity(i);
            return;
        } catch (Exception ignored) {
        }
        try {
            startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
        } catch (Exception e) {
            Toast.makeText(this, "Buka Setelan \u2192 Aksesibilitas manual", Toast.LENGTH_LONG).show();
        }
    }

    private boolean isAccessibilityEnabled() {
        if (CleanerService.isRunning() || UnrepostService.isRunning()) {
            return true;
        }
        try {
            String flat = Settings.Secure.getString(getContentResolver(),
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            return flat != null && (flat.contains(CleanerService.class.getName())
                    || flat.contains(UnrepostService.class.getName()));
        } catch (Exception e) {
            return false;
        }
    }

    // ---------------------------------------------------------------- refresh

    private final Runnable progressLoop = new Runnable() {
        @Override
        public void run() {
            refresh();
            if (CleanerState.isRunning(CleanerActivity.this)) {
                handler.postDelayed(this, 900L);
            }
        }
    };

    private void refresh() {
        boolean running = CleanerState.isRunning(this);
        boolean svcReady = CleanerService.isRunning();
        boolean a11y = isAccessibilityEnabled();

        if (running) {
            progressText.setTextColor(C_PINK);
            progressText.setText("Sedang berjalan \u00b7 " + CleanerState.progress(this)
                    + "\nDihapus: " + CleanerState.deleted(this));
            startButton.setText("Sedang berjalan\u2026");
            startButton.setEnabled(false);
            startButton.setBackground(roundedStroke(C_CARD, C_STROKE, 14));
            startButton.setTextColor(C_DIM);
            stopButton.setVisibility(View.VISIBLE);
        } else {
            startButton.setEnabled(true);
            stopButton.setVisibility(View.GONE);
            startButton.setText(dryRun ? "\u25B6  Mulai latihan" : "\u25B6  Mulai bersihkan repost");
            startButton.setTextColor(Color.parseColor("#0B0B0F"));
            startButton.setBackground(rounded(dryRun ? C_OK : C_CYAN, 14));

            int deleted = CleanerState.deleted(this);
            long finished = CleanerState.finishedAt(this);
            if (finished > 0) {
                progressText.setTextColor(CleanerState.wasAborted(this) ? C_PINK : C_OK);
                progressText.setText((CleanerState.wasAborted(this) ? "Berhenti" : "Selesai")
                        + " \u00b7 " + deleted + " repost diproses"
                        + (CleanerState.dryRun(this) ? " (latihan)" : ""));
            } else if (!a11y) {
                progressText.setTextColor(C_DIM);
                progressText.setText("Belum pernah dijalankan");
            } else if (!svcReady) {
                progressText.setTextColor(C_DIM);
                progressText.setText("Layanan siap \u2014 tekan Mulai");
            } else {
                progressText.setTextColor(C_DIM);
                progressText.setText("Layanan siap \u2014 tekan Mulai");
            }
        }

        String log = CleanerState.log(this);
        logText.setText(log.isEmpty() ? "Log akan muncul di sini." : log);
    }

    // ------------------------------------------------------------ view utils

    private LinearLayout card() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setPadding(dp(16), dp(16), dp(16), dp(16));
        l.setBackground(roundedStroke(C_CARD, C_STROKE, 18));
        return l;
    }

    private TextView label(String text) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f);
        t.setLetterSpacing(0.14f);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setTextColor(C_DIM);
        return t;
    }

    private GradientDrawable rounded(int color, int radiusDp) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(radiusDp));
        return d;
    }

    private GradientDrawable roundedStroke(int fill, int stroke, int radiusDp) {
        GradientDrawable d = rounded(fill, radiusDp);
        d.setStroke(dp(1), stroke);
        return d;
    }

    private View space(int dp) {
        View v = new View(this);
        v.setLayoutParams(new LinearLayout.LayoutParams(1, dp(dp)));
        return v;
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private LinearLayout.LayoutParams wrapWrap() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private LinearLayout.LayoutParams weightWrap(float weight) {
        return new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, weight);
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private abstract static class SimpleSeek implements SeekBar.OnSeekBarChangeListener {
        @Override
        public void onStartTrackingTouch(SeekBar seekBar) {
        }

        @Override
        public void onStopTrackingTouch(SeekBar seekBar) {
        }
    }
}
