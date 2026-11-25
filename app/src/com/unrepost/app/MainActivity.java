package com.unrepost.app;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

/**
 * Layar utama: status layanan, tombol aktifkan, uji teks caption (analisis lokal),
 * sensitivitas, opsi lewati otomatis, dan ringkasan statistik.
 */
public class MainActivity extends Activity {

    // palet (sama dengan res/values/colors.xml)
    private static final int C_BG = 0xFF0B0B0F;
    private static final int C_CARD = 0xFF15151C;
    private static final int C_CARD_STROKE = 0xFF26262F;
    private static final int C_CYAN = 0xFF25F4EE;
    private static final int C_PINK = 0xFFFE2C55;
    private static final int C_TEXT = 0xFFF4F4F6;
    private static final int C_TEXT_DIM = 0xFF9A9AA6;
    private static final int C_OK = 0xFF34D399;

    private TextView statusDot;
    private TextView statusText;
    private Button enableButton;
    private TextView statsText;
    private TextView resultText;
    private EditText sampleInput;
    private Switch skipSwitch;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(buildUi());
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    // -------------------------------------------------------------------- UI

    private View buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(C_BG);
        scroll.setFillViewport(true);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(28), dp(20), dp(32));
        scroll.addView(root, matchWrap());

        // Header
        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);

        TextView logo = new TextView(this);
        logo.setText("\u26D4");
        logo.setTextSize(TypedValue.COMPLEX_UNIT_SP, 30f);
        logo.setTextColor(getColor(R.color.pink));
        head.addView(logo, wrapWrap());

        LinearLayout titleBox = new LinearLayout(this);
        titleBox.setOrientation(LinearLayout.VERTICAL);
        titleBox.setPadding(dp(12), 0, 0, 0);

        TextView title = new TextView(this);
        title.setText("Unrepost");
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 26f);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(getColor(R.color.text));
        titleBox.addView(title, matchWrap());

        TextView sub = new TextView(this);
        sub.setText("Penyaring video repost TikTok \u00b7 tanpa server");
        sub.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f);
        sub.setTextColor(getColor(R.color.text_dim));
        titleBox.addView(sub, matchWrap());
        head.addView(titleBox, matchWrap());
        root.addView(head, matchWrap());
        root.addView(space(22));

        // Kartu status
        LinearLayout card = card();
        root.addView(card, matchWrap());
        card.addView(label("STATUS PENYARING"));

        LinearLayout statusRow = new LinearLayout(this);
        statusRow.setOrientation(LinearLayout.HORIZONTAL);
        statusRow.setGravity(Gravity.CENTER_VERTICAL);
        statusRow.setPadding(0, dp(10), 0, 0);

        statusDot = new TextView(this);
        statusDot.setText("\u25CF");
        statusDot.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f);
        statusRow.addView(statusDot, wrapWrap());

        statusText = new TextView(this);
        statusText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f);
        statusText.setTypeface(Typeface.DEFAULT_BOLD);
        statusText.setTextColor(getColor(R.color.text));
        statusText.setPadding(dp(8), 0, 0, 0);
        statusRow.addView(statusText, matchWrap());
        card.addView(statusRow, matchWrap());

        TextView hint = new TextView(this);
        hint.setText("Unrepost butuh izin Aksesibilitas untuk membaca caption di layar TikTok. "
                + "Aktifkan lewat: Setelan \u2192 Aksesibilitas \u2192 Aplikasi terinstal \u2192 Unrepost.");
        hint.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f);
        hint.setTextColor(getColor(R.color.text_dim));
        hint.setPadding(0, dp(10), 0, dp(14));
        card.addView(hint, matchWrap());

        enableButton = new Button(this);
        enableButton.setText("Buka pengaturan aksesibilitas");
        enableButton.setAllCaps(false);
        enableButton.setTextColor(Color.parseColor("#0B0B0F"));
        enableButton.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f);
        enableButton.setTypeface(Typeface.DEFAULT_BOLD);
        enableButton.setBackground(rounded(getColor(R.color.cyan), 14));
        enableButton.setPadding(dp(16), dp(12), dp(16), dp(12));
        enableButton.setOnClickListener(v -> openAccessibilitySettings());
        card.addView(enableButton, matchWrap());

        Button openTikTok = new Button(this);
        openTikTok.setText("Buka TikTok");
        openTikTok.setAllCaps(false);
        openTikTok.setTextColor(C_TEXT);
        openTikTok.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f);
        openTikTok.setBackground(roundedStroke(C_CARD, C_CARD_STROKE, 14));
        openTikTok.setPadding(dp(16), dp(12), dp(16), dp(12));
        LinearLayout.LayoutParams otLp = matchWrap();
        otLp.topMargin = dp(10);
        openTikTok.setOnClickListener(v -> openTikTok());
        card.addView(openTikTok, otLp);

        // Statistik
        root.addView(space(14));
        LinearLayout statsCard = card();
        root.addView(statsCard, matchWrap());
        statsCard.addView(label("HARI INI"));
        statsText = new TextView(this);
        statsText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f);
        statsText.setTextColor(getColor(R.color.text));
        statsText.setLineSpacing(dp(4), 1f);
        statsText.setPadding(0, dp(8), 0, 0);
        statsCard.addView(statsText, matchWrap());

        LinearLayout statsButtons = new LinearLayout(this);
        statsButtons.setOrientation(LinearLayout.HORIZONTAL);
        statsButtons.setPadding(0, dp(14), 0, 0);
        statsButtons.addView(smallButton("Riwayat repost", v -> {
            startActivity(new Intent(this, HistoryActivity.class));
        }), weightWrap(1));
        statsButtons.addView(space(10, true), wrapWrap());
        statsButtons.addView(smallButton("Reset angka", v -> {
            Prefs.resetStats(this);
            refresh();
            Toast.makeText(this, "Statistik direset", Toast.LENGTH_SHORT).show();
        }), weightWrap(1));
        statsCard.addView(statsButtons, matchWrap());

        // Setelan
        root.addView(space(14));
        LinearLayout settings = card();
        root.addView(settings, matchWrap());
        settings.addView(label("SETELAN PENYARING"));

        skipSwitch = new Switch(this);
        skipSwitch.setText("Lewati otomatis (geser ke video berikutnya)");
        skipSwitch.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f);
        skipSwitch.setTextColor(getColor(R.color.text));
        skipSwitch.setPadding(0, dp(12), 0, dp(6));
        skipSwitch.setChecked(Prefs.autoSkip(this));
        skipSwitch.setOnCheckedChangeListener((b, checked) -> {
            Prefs.get(this).edit().putBoolean(Prefs.KEY_AUTO_SKIP, checked).apply();
            if (checked) {
                Toast.makeText(this, "Repost akan dilewati otomatis", Toast.LENGTH_SHORT).show();
            }
        });
        settings.addView(skipSwitch, matchWrap());

        TextView sensLabel = label("SENSITIVITAS");
        sensLabel.setPadding(0, dp(16), 0, dp(8));
        settings.addView(sensLabel);

        final String[] levels = {"Konservatif", "Normal", "Agresif"};
        LinearLayout seg = new LinearLayout(this);
        seg.setOrientation(LinearLayout.HORIZONTAL);
        final Button[] segButtons = new Button[3];
        for (int i = 0; i < 3; i++) {
            final int level = i;
            Button b = new Button(this);
            b.setText(levels[i]);
            b.setAllCaps(false);
            b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f);
            b.setOnClickListener(v -> {
                Prefs.get(this).edit()
                        .putInt(Prefs.KEY_THRESHOLD, Prefs.thresholdForLevel(level)).apply();
                paintSegments(segButtons);
            });
            segButtons[i] = b;
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            if (i > 0) {
                lp.leftMargin = dp(8);
            }
            seg.addView(b, lp);
        }
        settings.addView(seg, matchWrap());
        paintSegments(segButtons);

        TextView sensHint = new TextView(this);
        sensHint.setText("Konservatif: hanya penanda jelas (\"repost\", \"credit:\"). "
                + "Agresif: ikut menandai caption dengan atribusi samar.");
        sensHint.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f);
        sensHint.setTextColor(getColor(R.color.text_dim));
        sensHint.setPadding(0, dp(10), 0, 0);
        settings.addView(sensHint, matchWrap());

        // Uji cepat
        root.addView(space(14));
        LinearLayout tester = card();
        root.addView(tester, matchWrap());
        tester.addView(label("UJI DETEKSI (LOKAL)"));
        TextView testHint = new TextView(this);
        testHint.setText("Tempel caption TikTok yang Anda lihat untuk melihat skor repost. "
                + "Tidak ada yang dikirim ke internet.");
        testHint.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f);
        testHint.setTextColor(getColor(R.color.text_dim));
        testHint.setPadding(0, dp(6), 0, dp(10));
        tester.addView(testHint, matchWrap());

        sampleInput = new EditText(this);
        sampleInput.setHint("mis. repost dari @kreator \u00b7 credit: @aslinya #fyp");
        sampleInput.setHintTextColor(getColor(R.color.text_dim));
        sampleInput.setTextColor(getColor(R.color.text));
        sampleInput.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f);
        sampleInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        sampleInput.setMinLines(3);
        sampleInput.setGravity(Gravity.TOP | Gravity.START);
        sampleInput.setBackground(roundedStroke(getColor(R.color.bg), getColor(R.color.card_stroke), 12));
        sampleInput.setPadding(dp(12), dp(10), dp(12), dp(10));
        tester.addView(sampleInput, matchWrap());

        Button testButton = new Button(this);
        testButton.setText("Analisis caption");
        testButton.setAllCaps(false);
        testButton.setTextColor(getColor(R.color.text));
        testButton.setBackground(roundedStroke(getColor(R.color.card), getColor(R.color.cyan), 12));
        testButton.setPadding(dp(14), dp(10), dp(14), dp(10));
        testButton.setOnClickListener(v -> runTest());
        LinearLayout.LayoutParams testLp = matchWrap();
        testLp.topMargin = dp(10);
        tester.addView(testButton, testLp);

        resultText = new TextView(this);
        resultText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f);
        resultText.setTextColor(getColor(R.color.text_dim));
        resultText.setLineSpacing(dp(3), 1f);
        resultText.setPadding(0, dp(12), 0, 0);
        tester.addView(resultText, matchWrap());

        TextView footnote = new TextView(this);
        footnote.setText("Unrepost hanya memakai teks di layar (caption, username, stiker). "
                + "Deteksi bersifat heuristik \u2014 bukan kebenaran absolut.");
        footnote.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f);
        footnote.setTextColor(getColor(R.color.text_dim));
        footnote.setPadding(0, dp(18), 0, 0);
        root.addView(footnote, matchWrap());

        return scroll;
    }

    private void paintSegments(Button[] buttons) {
        int selected = Prefs.sensitivityLevel(this);
        for (int i = 0; i < buttons.length; i++) {
            Button b = buttons[i];
            if (i == selected) {
                b.setTextColor(Color.parseColor("#0B0B0F"));
                b.setBackground(rounded(getColor(R.color.cyan), 12));
                b.setTypeface(Typeface.DEFAULT_BOLD);
            } else {
                b.setTextColor(getColor(R.color.text_dim));
                b.setBackground(roundedStroke(getColor(R.color.card), getColor(R.color.card_stroke), 12));
                b.setTypeface(Typeface.DEFAULT);
            }
        }
    }

    private void runTest() {
        String sample = sampleInput.getText().toString();
        if (sample.trim().isEmpty()) {
            resultText.setTextColor(getColor(R.color.text_dim));
            resultText.setText("Tulis atau tempel caption dulu.");
            return;
        }
        RepostDetector.Result r = RepostDetector.analyze(sample, Prefs.threshold(this));
        resultText.setTextColor(r.isRepost ? getColor(R.color.pink) : getColor(R.color.ok));
        String head = r.isRepost
                ? "\u26D4 REPOST terdeteksi \u2014 skor " + r.score + " / ambang " + r.threshold
                : "\u2713 Tidak terdeteksi repost \u2014 skor " + r.score + " / ambang " + r.threshold;
        resultText.setText(head + "\n" + r.reasonText());
    }

    // ----------------------------------------------------------------- status

    private void refresh() {
        boolean running = UnrepostService.isRunning();
        boolean enabled = Prefs.filterEnabled(this);
        boolean a11yOn = isAccessibilityServiceEnabled();

        if (running && enabled) {
            statusDot.setTextColor(getColor(R.color.ok));
            statusText.setText("Aktif \u2014 memantau TikTok");
            enableButton.setText("Penyaring aktif");
        } else if (a11yOn) {
            statusDot.setTextColor(getColor(R.color.cyan));
            statusText.setText(enabled ? "Layanan aktif" : "Izin ada, penyaring dimatikan");
            enableButton.setText(enabled ? "Penyaring aktif" : "Aktifkan penyaring");
        } else {
            statusDot.setTextColor(getColor(R.color.pink));
            statusText.setText("Belum aktif");
            enableButton.setText("Buka pengaturan aksesibilitas");
        }

        int todaySeen = Prefs.todaySeen(this);
        int todaySkipped = Prefs.todaySkipped(this);
        int totalSeen = Prefs.get(this).getInt(Prefs.KEY_TOTAL_SEEN, 0);
        int totalSkipped = Prefs.get(this).getInt(Prefs.KEY_TOTAL_SKIPPED, 0);
        statsText.setText("Repost terdeteksi: " + todaySeen
                + "\nDilewati otomatis: " + todaySkipped
                + "\n\nTotal sepanjang waktu: " + totalSeen + " terdeteksi \u00b7 "
                + totalSkipped + " dilewati");
        if (skipSwitch != null) {
            skipSwitch.setChecked(Prefs.autoSkip(this));
        }
    }

    private boolean isAccessibilityServiceEnabled() {
        if (UnrepostService.isRunning()) {
            return true;
        }
        try {
            String flat = android.provider.Settings.Secure.getString(
                    getContentResolver(),
                    android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            if (flat != null) {
                return flat.contains(getPackageName() + "/" + UnrepostService.class.getName());
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    private void openTikTok() {
        String[] pkgs = {"com.zhiliaoapp.musically", "com.ss.android.ugc.trill",
                "com.ss.android.ugc.aweme", "com.tiktok.lite"};
        for (String pkg : pkgs) {
            Intent i = getPackageManager().getLaunchIntentForPackage(pkg);
            if (i != null) {
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(i);
                return;
            }
        }
        Toast.makeText(this, "TikTok belum terpasang di perangkat ini", Toast.LENGTH_LONG).show();
    }

    private void openAccessibilitySettings() {
        if (UnrepostService.isRunning() && Prefs.filterEnabled(this)) {
            // sudah aktif: tombol berfungsi sebagai toggle penyaring
            Prefs.get(this).edit().putBoolean(Prefs.KEY_ENABLED, false).apply();
            Toast.makeText(this, "Penyaring dimatikan", Toast.LENGTH_SHORT).show();
            refresh();
            return;
        }
        if (isAccessibilityServiceEnabled()) {
            Prefs.get(this).edit().putBoolean(Prefs.KEY_ENABLED, true).apply();
            Toast.makeText(this, "Penyaring diaktifkan", Toast.LENGTH_SHORT).show();
            refresh();
            return;
        }
        try {
            Intent i = new Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
            Toast.makeText(this, "Cari \"Unrepost\" lalu aktifkan", Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(this, "Buka Setelan \u2192 Aksesibilitas secara manual",
                    Toast.LENGTH_LONG).show();
        }
    }

    // ------------------------------------------------------------ view utils

    private LinearLayout card() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setPadding(dp(16), dp(16), dp(16), dp(16));
        l.setBackground(roundedStroke(getColor(R.color.card), getColor(R.color.card_stroke), 18));
        return l;
    }

    private TextView label(String text) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f);
        t.setLetterSpacing(0.14f);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setTextColor(getColor(R.color.text_dim));
        return t;
    }

    private Button smallButton(String text, View.OnClickListener l) {
        Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f);
        b.setTextColor(getColor(R.color.text));
        b.setBackground(roundedStroke(getColor(R.color.card), getColor(R.color.card_stroke), 12));
        b.setOnClickListener(l);
        return b;
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

    private View space(int dp, boolean horizontal) {
        View v = new View(this);
        v.setLayoutParams(horizontal
                ? new LinearLayout.LayoutParams(dp(dp), 1)
                : new LinearLayout.LayoutParams(1, dp(dp)));
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
}
