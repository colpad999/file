package com.unrepost.app;

import android.app.Activity;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** Daftar repost yang pernah ditandai, terbaru di atas. */
public class HistoryActivity extends Activity {

    private static final int C_BG = 0xFF0B0B0F;
    private static final int C_CARD = 0xFF15151C;
    private static final int C_STROKE = 0xFF26262F;
    private static final int C_CYAN = 0xFF25F4EE;
    private static final int C_TEXT = 0xFFF4F4F6;
    private static final int C_DIM = 0xFF9A9AA6;

    private final List<RepostLog.Entry> items = new ArrayList<>();
    private Adapter adapter;
    private ListView list;
    private View empty;
    private TextView countLabel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(C_BG);

        // header
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(20), dp(22), dp(20), dp(10));

        TextView title = new TextView(this);
        title.setText("Riwayat Repost");
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(C_TEXT);
        header.addView(title, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        Button clear = new Button(this);
        clear.setText("Hapus");
        clear.setAllCaps(false);
        clear.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f);
        clear.setTextColor(C_DIM);
        GradientDrawable cbg = new GradientDrawable();
        cbg.setColor(C_CARD);
        cbg.setCornerRadius(dp(10));
        cbg.setStroke(dp(1), C_STROKE);
        clear.setBackground(cbg);
        clear.setOnClickListener(v -> {
            RepostLog.clear(this);
            load();
        });
        header.addView(clear);
        root.addView(header);

        countLabel = new TextView(this);
        countLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f);
        countLabel.setTextColor(C_DIM);
        countLabel.setPadding(dp(20), 0, dp(20), dp(10));
        root.addView(countLabel);

        FrameLayout frame = new FrameLayout(this);
        list = new ListView(this);
        list.setDivider(null);
        list.setDividerHeight(0);
        list.setSelector(android.R.color.transparent);
        list.setPadding(0, 0, 0, dp(16));
        list.setClipToPadding(false);
        frame.addView(list, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        empty = buildEmpty();
        frame.addView(empty, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        root.addView(frame, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        setContentView(root);

        adapter = new Adapter();
        list.setAdapter(adapter);
        load();
    }

    private View buildEmpty() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        box.setPadding(dp(36), dp(24), dp(36), dp(60));

        TextView icon = new TextView(this);
        icon.setText("\u26D4");
        icon.setTextSize(TypedValue.COMPLEX_UNIT_SP, 40f);
        icon.setTextColor(0xFF3A3A46);
        box.addView(icon);

        TextView msg = new TextView(this);
        msg.setText("Belum ada repost terdeteksi.");
        msg.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f);
        msg.setTextColor(C_TEXT);
        msg.setGravity(Gravity.CENTER);
        msg.setPadding(0, dp(12), 0, dp(6));
        box.addView(msg);

        TextView hint = new TextView(this);
        hint.setText("Aktifkan penyaring di layar utama, buka TikTok, lalu geser beberapa video. "
                + "Video yang ditandai akan muncul di sini.");
        hint.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f);
        hint.setTextColor(C_DIM);
        hint.setGravity(Gravity.CENTER);
        box.addView(hint);
        return box;
    }

    private void load() {
        items.clear();
        items.addAll(RepostLog.list(this));
        adapter.notifyDataSetChanged();
        boolean none = items.isEmpty();
        empty.setVisibility(none ? View.VISIBLE : View.GONE);
        list.setVisibility(none ? View.GONE : View.VISIBLE);
        countLabel.setText(none ? "" : items.size() + " video ditandai \u00b7 tersimpan lokal");
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private class Adapter extends BaseAdapter {
        private final SimpleDateFormat fmt =
                new SimpleDateFormat("dd MMM yyyy \u00b7 HH:mm", new Locale("id", "ID"));

        @Override
        public int getCount() {
            return items.size();
        }

        @Override
        public Object getItem(int position) {
            return items.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            LinearLayout row;
            TextView title;
            TextView meta;
            if (convertView instanceof LinearLayout) {
                row = (LinearLayout) convertView;
                title = (TextView) row.getChildAt(0);
                meta = (TextView) row.getChildAt(1);
            } else {
                row = new LinearLayout(HistoryActivity.this);
                row.setOrientation(LinearLayout.VERTICAL);
                row.setPadding(dp(16), dp(12), dp(16), dp(12));

                title = new TextView(HistoryActivity.this);
                title.setTextColor(C_TEXT);
                title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f);
                title.setLineSpacing(dp(2), 1f);
                row.addView(title);

                meta = new TextView(HistoryActivity.this);
                meta.setTextColor(C_DIM);
                meta.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f);
                meta.setPadding(0, dp(6), 0, 0);
                row.addView(meta);

                GradientDrawable bg = new GradientDrawable();
                bg.setColor(C_CARD);
                bg.setCornerRadius(dp(14));
                bg.setStroke(dp(1), C_STROKE);
                row.setBackground(bg);

                LinearLayout wrapper = new LinearLayout(HistoryActivity.this);
                wrapper.setPadding(dp(14), dp(6), dp(14), dp(6));
                wrapper.addView(row, new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                return wrapper;
            }
            RepostLog.Entry e = items.get(position);
            String t = e.title == null || e.title.isEmpty() ? "(caption tidak terbaca)" : e.title;
            title.setText(t);
            String when = e.time > 0 ? fmt.format(new Date(e.time)) : "waktu tidak diketahui";
            meta.setText(when + "  \u00b7  #" + e.signature);
            return row;
        }
    }
}
