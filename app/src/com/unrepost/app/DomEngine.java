package com.unrepost.app;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Mesin otomasi berbasis DOM halaman TikTok (bukan API internal).
 *
 * <p>Alurnya: buka halaman repost di profil → tunggu ada tombol menu → buka menu →
 * cari "Hapus repost" → konfirmasi → kembali → jeda → video berikutnya.</p>
 *
 * <p>Murni logika (tanpa Android) sehingga perilakunya bisa diuji dengan HTML
 * asli/sampel lewat jsdom di web-test.</p>
 */
public final class DomEngine {

    public enum Action {
        WAIT, OPEN_REPOSTS, TAP_NODE, TAP_MENU, TAP_REMOVE, TAP_CONFIRM, GO_BACK, SCROLL,
        FINISH, ABORT
    }

    public static final class Decision {
        public final Action action;
        public final String sel;
        public final String note;

        Decision(Action action, String sel, String note) {
            this.action = action;
            this.sel = sel;
            this.note = note;
        }

        @Override
        public String toString() {
            return action + (sel == null ? "" : " [" + sel + "]") + (note == null ? "" : " — " + note);
        }
    }

    // ------------------------------------------------------------------ kamus

    /** Tombol pembuka menu (bagikan / lainnya / titik tiga — termasuk versi mobile web). */
    private static final String[][] MENU_SELECTORS = {
            {"[data-e2e=share-icon]", "[data-e2e=share-icon]"},
            {"[data-e2e=browse-more]", "[data-e2e=browse-more]"},
            {"[data-e2e=more-menu]", "[data-e2e=more-menu]"},
            {"[data-e2e=video-share-icon]", "[data-e2e=video-share-icon]"},
            {"[data-e2e=repost-menu]", "[data-e2e=repost-menu]"},
            {"aria-label", "(?i)(more|more options|others|lainnya|opsi lain)"},
            {"aria-label", "(?i)(share|bagikan)"},
            {"title", "(?i)(more|others|lainnya)"}
    };

    /** Aksi hapus di dalam menu. */
    private static final String[] REMOVE_TEXTS = {
            "(?i)hapus\\s+repost", "(?i)remove\\s+repost", "(?i)batalkan\\s+repost",
            "(?i)undo\\s+repost", "(?i)hapus\\s+dari\\s+repost", "(?i)remove\\s+from\\s+repost",
            "(?i)^unrepost$", "(?i)hapus\\s+re-?post"
    };

    /** Tombol konfirmasi pada dialog. */
    private static final String[] CONFIRM_TEXTS = {
            "(?i)^hapus$", "(?i)^remove$", "(?i)^hapus\\s+repost$", "(?i)^ya$", "(?i)^yes$",
            "(?i)^konfirmasi$", "(?i)^confirm$", "(?i)^ok(e)?$"
    };

    /** Tanda halaman login (artinya sesi belum ada). */
    private static final Pattern LOGIN_HINT = Pattern.compile(
            "(?i)(log\\s*in|masuk|sign\\s*up|daftar|password|sandi)");

    private static final Pattern REPOST_URL = Pattern.compile(
            "(?i)(repost|reposted)");

    // ------------------------------------------------------------------ state

    private enum Step {
        START, OPEN_REPOSTS, VIDEO, MENU, REMOVE, CONFIRM, CONFIRMED, BACK, SCROLL, DONE, DEAD
    }

    private final int maxDeletes;
    private final long gapMs;
    private final boolean dryRun;
    private String username = "";

    private Step step = Step.START;
    private int deleted;
    private int attempts;
    private int scrolls;
    private long nextAt;
    private String note = "";
    private final List<String> log = new ArrayList<>();

    public DomEngine(int maxDeletes, long gapMs, boolean dryRun) {
        this.maxDeletes = Math.max(1, maxDeletes);
        this.gapMs = Math.max(2000L, gapMs);
        this.dryRun = dryRun;
    }

    public void setUsername(String username) {
        this.username = username == null ? "" : username.replace("@", "");
    }

    public int deleted() {
        return deleted;
    }

    public boolean done() {
        return step == Step.DONE || step == Step.DEAD;
    }

    public boolean aborted() {
        return step == Step.DEAD;
    }

    public String note() {
        return note;
    }

    public int stepNumber() {
        switch (step) {
            case START: return 1;
            case OPEN_REPOSTS: return 2;
            case VIDEO: return 3;
            case MENU: return 4;
            case REMOVE: return 5;
            case CONFIRM: return 6;
            case BACK: return 7;
            case SCROLL: return 8;
            default: return 9;
        }
    }

    public String progressText() {
        return (dryRun ? "LATIHAN" : "HAPUS") + " " + deleted + "/" + maxDeletes
                + " · langkah " + stepNumber() + "/8";
    }

    public List<String> logLines() {
        return new ArrayList<>(log);
    }

    /** URL halaman repost milik sendiri. */
    public static String repostUrl(String username) {
        return "https://www.tiktok.com/@" + (username == null ? "" : username) + "?lang=id";
    }

    /**
     * Satu tick: berikan snapshot DOM terakhir + info halaman, dapatkan aksi berikutnya.
     *
     * @param html        snapshot HTML (tag data-ur) dari halaman
     * @param url         URL halaman saat ini
     * @param now         waktu (ms)
     * @param pageLoading true bila halaman masih memuat
     */
    public Decision onTick(String html, String url, long now, boolean pageLoading) {
        if (done()) {
            return new Decision(step == Step.DONE ? Action.FINISH : Action.ABORT, null, note);
        }
        if (now < nextAt) {
            return new Decision(Action.WAIT, null, "menunggu");
        }
        String page = html == null ? "" : html;
        String lower = page.toLowerCase(Locale.ROOT);
        List<Node> nodes = parse(page);

        if (nodes.isEmpty() && pageLoading) {
            return retry(now, "halaman dimuat");
        }
        if (nodes.isEmpty()) {
            // mungkin halaman belum siap / blank
            return retry(now, "DOM kosong");
        }

        switch (step) {
            case START:
                log("halaman terbuka: " + shortUrl(url));
                step = Step.OPEN_REPOSTS;
                nextAt = now + 2500;
                return new Decision(Action.OPEN_REPOSTS, repostUrl(username), "buka halaman repost");

            case OPEN_REPOSTS: {
                if (isLoginPage(nodes)) {
                    step = Step.DEAD;
                    note = "Belum login di halaman TikTok";
                    log("BERHENTI: halaman login terdeteksi — login dulu");
                    return new Decision(Action.ABORT, null, note);
                }
                Node tab = findByText(nodes, "(?i)^\\s*repost(s)?\\s*$");
                if (tab != null) {
                    log("ketuk tab Repost");
                    step = Step.VIDEO;
                    attempts = 0;
                    nextAt = now + 2600;
                    return new Decision(Action.TAP_NODE, tab.ur, "tab Repost");
                }
                if (countVideoLinks(nodes) > 0) {
                    log("grid repost terlihat (" + countVideoLinks(nodes) + " video)");
                    step = Step.VIDEO;
                    attempts = 0;
                    nextAt = now + 1200;
                    return new Decision(Action.WAIT, null, "grid terbaca");
                }
                return retry(now, "tab/grid Repost tidak terlihat");
            }

            case VIDEO: {
                if (deleted >= maxDeletes) {
                    log("batas " + maxDeletes + " tercapai");
                    step = Step.DONE;
                    return new Decision(Action.FINISH, null, "batas tercapai");
                }
                if (isLoginPage(nodes)) {
                    step = Step.DEAD;
                    note = "Sesi login hilang";
                    return new Decision(Action.ABORT, null, note);
                }
                // Sudah di halaman video? (ada video/atas nama penulis)
                if (hasVideoPlayer(page)) {
                    Node menu = findMenuButton(nodes);
                    if (menu != null) {
                        log("buka menu video");
                        step = Step.MENU;
                        attempts = 0;
                        nextAt = now + 1800;
                        return new Decision(Action.TAP_MENU, menu.ur, "buka menu");
                    }
                    return retry(now, "tombol menu belum ada");
                }
                // Masih di grid: ketuk video pertama
                Node first = firstVideoLink(nodes);
                if (first != null) {
                    log("buka video #" + (deleted + 1));
                    step = Step.MENU;
                    attempts = 0;
                    nextAt = now + 3200;
                    return new Decision(Action.TAP_NODE, first.ur, "buka video");
                }
                if (scrolls < 6) {
                    scrolls++;
                    log("gulir grid untuk mencari video");
                    step = Step.SCROLL;
                    nextAt = now + 1500;
                    return new Decision(Action.SCROLL, null, "gulir");
                }
                log("tidak ada video repost lagi");
                step = Step.DONE;
                return new Decision(Action.FINISH, null, "repost habis");
            }

            case SCROLL: {
                step = Step.VIDEO;
                attempts = 0;
                return new Decision(Action.WAIT, null, "lanjut");
            }

            case MENU: {
                if (deleted >= maxDeletes) {
                    step = Step.DONE;
                    return new Decision(Action.FINISH, null, "batas tercapai");
                }
                Node remove = findByText(nodes, REMOVE_TEXTS);
                if (remove != null) {
                    log("ketuk \"hapus repost\"");
                    step = Step.REMOVE;
                    attempts = 0;
                    nextAt = now + 1200;
                    return new Decision(Action.TAP_REMOVE, remove.ur, "hapus repost");
                }
                if (findConfirm(nodes) != null && !hasVideoPlayer(page)) {
                    // sudah hapus tanpa menu
                    return countOne(now, "terhapus");
                }
                Node menu = findMenuButton(nodes);
                if (menu != null && attempts < 2) {
                    attempts++;
                    nextAt = now + 1800;
                    return new Decision(Action.TAP_MENU, menu.ur, "coba buka menu lagi");
                }
                return retry(now, "aksi hapus tidak ditemukan di menu");
            }

            case REMOVE: {
                Node confirm = findConfirm(nodes);
                if (confirm != null) {
                    log("konfirmasi hapus");
                    step = Step.CONFIRM;
                    attempts = 0;
                    nextAt = now + 1200;
                    return new Decision(Action.TAP_CONFIRM, confirm.ur, "konfirmasi");
                }
                if (deleted >= maxDeletes) {
                    step = Step.DONE;
                    return new Decision(Action.FINISH, null, "batas");
                }
                // tidak ada dialog konfirmasi → klik hapus tadi langsung berlaku
                return countOne(now, "hapus tanpa konfirmasi");
            }

            case CONFIRM: {
                if (deleted >= maxDeletes) {
                    step = Step.DONE;
                    return new Decision(Action.FINISH, null, "batas");
                }
                // Setelah menekan konfirmasi: hitung di sini (dialog sudah hilang / berganti).
                step = Step.CONFIRMED;
                nextAt = now + 300;
                return new Decision(Action.WAIT, null, "menunggu hasil hapus");
            }

            case CONFIRMED: {
                // Konfirmasi sudah dieksekusi pada tick sebelumnya.
                return countOne(now, "repost dihapus");
            }

            case BACK: {
                if (findByText(nodes, REMOVE_TEXTS) != null) {
                    // masih di menu, mundur lagi
                    step = Step.MENU;
                    return new Decision(Action.WAIT, null, "diam");
                }
                step = Step.VIDEO;
                attempts = 0;
                nextAt = now + 2200;
                return new Decision(Action.WAIT, null, "kembali ke grid");
            }

            default:
                return new Decision(Action.WAIT, null, note);
        }
    }

    private Decision countOne(long now, String why) {
        deleted++;
        log((dryRun ? "[latihan] " : "") + why + " — total " + deleted);
        if (deleted >= maxDeletes) {
            log("batas " + maxDeletes + " tercapai — selesai");
            note = "batas tercapai";
            step = Step.DONE;
            return new Decision(Action.FINISH, null, note);
        }
        step = Step.BACK;
        attempts = 0;
        nextAt = now + Math.max(1200L, gapMs / 3);
        return new Decision(Action.GO_BACK, null, why);
    }

    /** Paksa selesai (tombol Berhenti). */
    public void stop() {
        if (!done()) {
            log("dihentikan pengguna pada " + deleted);
            step = Step.DONE;
            note = "dihentikan";
        }
    }

    /** Beri kesempatan terbatas sebelum menyerah. */
    private Decision retry(long now, String why) {
        attempts++;
        note = why;
        if (attempts > 25) {
            log("BERHENTI: " + why);
            step = Step.DEAD;
            return new Decision(Action.ABORT, null, why);
        }
        nextAt = now + 800;
        return new Decision(Action.WAIT, null, why + " (" + attempts + "/25)");
    }

    private void log(String line) {
        log.add(line);
        while (log.size() > 150) {
            log.remove(0);
        }
        note = line;
    }

    private static String shortUrl(String url) {
        if (url == null) {
            return "?";
        }
        return url.length() > 70 ? url.substring(0, 67) + "…" : url;
    }

    // ------------------------------------------------------------- HTML nodes

    /** Node ringkas hasil parse HTML (atribut relevan + teks). */
    public static final class Node {
        public final String tag;
        public final String ur;
        public final String e2e;
        public final String aria;
        public final String role;
        public final String href;
        public final String text;
        public final int depth;

        Node(String tag, String ur, String e2e, String aria, String role, String href,
             String text, int depth) {
            this.tag = tag;
            this.ur = ur;
            this.e2e = e2e;
            this.aria = aria;
            this.role = role;
            this.href = href;
            this.text = text;
            this.depth = depth;
        }

        @Override
        public String toString() {
            return tag + "#" + ur + " e2e=" + e2e + " aria=" + aria + " text=" + text;
        }
    }

    private static final Pattern TAG = Pattern.compile(
            "<(/?)([a-z0-9]+)((?:\\s+[a-z0-9:_-]+=\"[^\"]*\")*)\\s*>", Pattern.CASE_INSENSITIVE);
    private static final Pattern ATTR = Pattern.compile(
            "([a-z0-9:_-]+)=\"([^\"]*)\"", Pattern.CASE_INSENSITIVE);

    /** Parse snapshot HTML jadi daftar node beserta teks langsungnya. */
    public static List<Node> parse(String html) {
        List<Node> out = new ArrayList<>();
        if (html == null || html.isEmpty()) {
            return out;
        }
        Matcher m = TAG.matcher(html);
        int depth = 0;
        while (m.find()) {
            boolean close = !m.group(1).isEmpty();
            String tag = m.group(2).toLowerCase(Locale.ROOT);
            if (close) {
                depth = Math.max(0, depth - 1);
                continue;
            }
            Map<String, String> attrs = new LinkedHashMap<>();
            Matcher am = ATTR.matcher(m.group(3));
            while (am.find()) {
                attrs.put(am.group(1).toLowerCase(Locale.ROOT), am.group(2));
            }
            // teks langsung setelah tag ini sampai tag berikutnya
            int start = m.end();
            int next = m.end();
            Matcher m2 = TAG.matcher(html);
            String text = "";
            if (m2.find(start)) {
                next = m2.start();
                text = html.substring(start, next);
            } else {
                text = html.substring(start);
                next = html.length();
            }
            text = text.replaceAll("<[^>]*>", " ").replaceAll("\\s+", " ").trim();
            String ur = attrs.get("data-ur");
            out.add(new Node(tag, ur, attrs.get("data-e2e"), attrs.get("aria-label"),
                    attrs.get("role"), attrs.get("href"), text, depth));
            depth++;
        }
        return out;
    }

    static Node findByText(List<Node> nodes, String[] regexes) {
        for (String re : regexes) {
            Node n = findByText(nodes, re);
            if (n != null) {
                return n;
            }
        }
        return null;
    }

    static Node findByText(List<Node> nodes, String regex) {
        Pattern p;
        try {
            p = Pattern.compile(regex);
        } catch (Exception e) {
            return null;
        }
        Node best = null;
        for (Node n : nodes) {
            if (n.ur == null) {
                continue;
            }
            String label = n.text != null && !n.text.isEmpty() ? n.text : n.aria;
            if (label == null) {
                continue;
            }
            if (p.matcher(label.trim()).find()) {
                if (best == null || label.length() < bestLabelLen(best)) {
                    best = n;
                }
            }
        }
        return best;
    }

    private static int bestLabelLen(Node n) {
        String l = n.text != null && !n.text.isEmpty() ? n.text : n.aria;
        return l == null ? 0 : l.length();
    }

    private static Node findConfirm(List<Node> nodes) {
        return findByText(nodes, CONFIRM_TEXTS);
    }

    private static boolean isLoginPage(List<Node> nodes) {
        boolean password = false;
        for (Node n : nodes) {
            if ("input".equals(n.tag) && "password".equals(n.e2e)) {
                password = true;
            }
            if (n.text != null && LOGIN_HINT.matcher(n.text).find()
                    && n.text.length() < 40 && n.text.toLowerCase(Locale.ROOT).contains("masuk")) {
                password = true;
            }
        }
        return password;
    }

    private static boolean hasVideoPlayer(String html) {
        return html.matches("(?s).*<video[ >].*") || html.contains("data-e2e=\"browse-video\"");
    }

    private static int countVideoLinks(List<Node> nodes) {
        int c = 0;
        for (Node n : nodes) {
            if ("a".equals(n.tag) && n.href != null && n.href.matches(".*/video/\\d+.*")) {
                c++;
            }
        }
        return c;
    }

    private static Node firstVideoLink(List<Node> nodes) {
        for (Node n : nodes) {
            if ("a".equals(n.tag) && n.href != null && n.href.matches(".*/video/\\d+.*") && n.ur != null) {
                return n;
            }
        }
        return null;
    }

    /** Cari tombol menu/bagikan: utamakan penanda data-e2e, lalu aria-label, lalu teks. */
    private static Node findMenuButton(List<Node> nodes) {
        for (String[] pair : MENU_SELECTORS) {
            String key = pair[0];
            String value = pair[1];
            if ("[data-e2e=share-icon]".equals(key)) {
                Node n = byE2e(nodes, "share-icon");
                if (n != null) {
                    return n;
                }
                continue;
            }
            if ("[data-e2e=browse-more]".equals(key)) {
                Node n = byE2e(nodes, "browse-more");
                if (n != null) {
                    return n;
                }
                continue;
            }
            if ("[data-e2e=more-menu]".equals(key)) {
                Node n = byE2e(nodes, "more-menu");
                if (n != null) {
                    return n;
                }
                continue;
            }
            if ("[data-e2e=video-share-icon]".equals(key)) {
                Node n = byE2e(nodes, "video-share-icon");
                if (n != null) {
                    return n;
                }
                continue;
            }
            if ("[data-e2e=repost-menu]".equals(key)) {
                Node n = byE2e(nodes, "repost-menu");
                if (n != null) {
                    return n;
                }
                continue;
            }
            if ("aria-label".equals(key)) {
                Pattern p = Pattern.compile(value);
                for (Node n : nodes) {
                    if (n.ur != null && n.aria != null && p.matcher(n.aria).find()) {
                        return n;
                    }
                }
                continue;
            }
            if ("title".equals(key)) {
                Pattern p = Pattern.compile(value);
                for (Node n : nodes) {
                    if (n.ur != null && n.aria != null && p.matcher(n.aria).find()) {
                        return n;
                    }
                }
            }
        }
        return findByText(nodes, "(?i)^(\\s*)(bagikan|share|lainnya|more|opsi)(\\s*)$");
    }

    private static Node byE2e(List<Node> nodes, String value) {
        for (Node n : nodes) {
            if (value.equals(n.e2e) && n.ur != null) {
                return n;
            }
        }
        return null;
    }
}
