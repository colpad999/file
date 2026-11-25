package com.unrepost.app;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Mesin otomasi "Bersihkan Repost": menghapus repost dari profil TikTok sendiri
 * secara bertahap (satu video per siklus, dengan jeda), dengan batas jumlah,
 * mode latihan (dry-run), dan tombol berhenti.
 *
 * <p>Mesin ini murni logika (tanpa Android) supaya transisinya bisa diuji di desktop.
 * Layanan aksesibilitas memberi daftar label UI yang terlihat, lalu menjalankan
 * aksi yang diputuskan mesin ini.</p>
 *
 * <p>Alur: Profil → tab Repost → item pertama → menu → "Hapus repost" → konfirmasi →
 * jeda → ulangi.</p>
 */
public final class CleanerEngine {

    public enum Action {
        /** Tidak ada yang dilakukan pada tick ini. */
        WAIT,
        /** Ketuk tab Profil / "Saya" di navigasi bawah. */
        TAP_PROFILE,
        /** Ketuk tab "Repost" di profil. */
        TAP_REPOST_TAB,
        /** Ketuk video pertama di grid repost (tanpa label). */
        TAP_FIRST_ITEM,
        /** Ketuk tombol bagikan / titik tiga untuk membuka menu. */
        TAP_MENU,
        /** Ketuk "Hapus repost" / "Batalkan repost" di menu. */
        TAP_REMOVE,
        /** Ketuk tombol konfirmasi "Hapus". */
        TAP_CONFIRM,
        /** Semua beres (batas tercapai / tidak ada repost lagi). */
        FINISH,
        /** Menyerah karena layar tidak dikenali. */
        ABORT
    }

    public static final class Decision {
        public final Action action;
        public final String label;
        public final String note;

        Decision(Action action, String label, String note) {
            this.action = action;
            this.label = label;
            this.note = note;
        }

        @Override
        public String toString() {
            return action + (label == null ? "" : " \"" + label + "\"")
                    + (note == null ? "" : " (" + note + ")");
        }
    }

    // ------------------------------------------------------------------ kamus

    private static final String[] PROFILE_TABS = {
            "profil", "profile", "saya", "me", "mein", "akun", "account"
    };
    private static final String[] REPOST_TABS = {
            "repost", "reposted", "reposts", "repostan", "rep", "di-repost"
    };
    private static final String[] NO_REPOST_HINTS = {
            "belum ada repost", "tidak ada repost", "no repost", "no reposts yet",
            "belum ada video", "when you repost", "saat kamu merepost"
    };
    private static final String[] MENU_TRIGGERS = {
            "bagikan", "share", "lainnya", "more", "opsi", "options", "menu",
            "bagikan video", "share video"
    };
    private static final String[] REMOVE_ACTIONS = {
            "hapus repost", "remove repost", "batalkan repost", "undo repost",
            "hapus dari repost", "remove from repost", "repost", "unrepost",
            "hapus video", "remove video"
    };
    private static final String[] CONFIRM_ACTIONS = {
            "hapus", "remove", "hapus repost", "remove repost", "ya", "yes",
            "konfirmasi", "confirm", "ok", "oke"
    };

    // ------------------------------------------------------------------ state

    private enum Step { FIND_PROFILE, REPOST_TAB, GRID, MENU, REMOVE, CONFIRM, GAP, FINISHED, ABORTED }

    private final int maxDeletes;
    private final int gapMs;
    private final boolean dryRun;

    private Step step = Step.FIND_PROFILE;
    private int deleted;
    private int attempt;
    private long nextTickAt;
    private boolean tappedMenuOnce;
    private String lastNote = "";
    private final List<String> log = new ArrayList<>();
    private final List<String> seenLabels = new ArrayList<>();

    public CleanerEngine(int maxDeletes, int gapMs, boolean dryRun) {
        this.maxDeletes = Math.max(1, maxDeletes);
        this.gapMs = Math.max(3000, gapMs);
        this.dryRun = dryRun;
    }

    public int deleted() {
        return deleted;
    }

    public int maxDeletes() {
        return maxDeletes;
    }

    public boolean isDryRun() {
        return dryRun;
    }

    public boolean finished() {
        return step == Step.FINISHED;
    }

    public boolean aborted() {
        return step == Step.ABORTED;
    }

    public boolean done() {
        return finished() || aborted();
    }

    public String lastNote() {
        return lastNote;
    }

    public int stepNumber() {
        switch (step) {
            case FIND_PROFILE: return 1;
            case REPOST_TAB: return 2;
            case GRID: return 3;
            case MENU: return 4;
            case REMOVE: return 5;
            case CONFIRM: return 6;
            case GAP: return 7;
            default: return 8;
        }
    }

    public List<String> logLines() {
        return new ArrayList<>(log);
    }

    /** Label terakhir yang terlihat (untuk diagnosa bila UI tidak dikenali). */
    public List<String> lastSeenLabels() {
        return new ArrayList<>(seenLabels);
    }

    public String progressText() {
        String mode = dryRun ? "LATIHAN" : "HAPUS";
        return mode + " " + deleted + "/" + maxDeletes + " \u00b7 langkah " + stepNumber() + "/7";
    }

    /**
     * Dipanggil berkala oleh layanan selama proses berjalan.
     *
     * @param labels    semua teks & contentDescription yang terlihat (huruf asli)
     * @param dialogOpen true bila ada dialog/lembar aksi yang terbuka
     * @param now       waktu sekarang (ms)
     */
    public Decision onTick(List<String> labels, boolean dialogOpen, long now) {
        if (done()) {
            return new Decision(step == Step.FINISHED ? Action.FINISH : Action.ABORT, null, lastNote);
        }
        if (now < nextTickAt) {
            return new Decision(Action.WAIT, null, "menunggu");
        }
        seenLabels.clear();
        if (labels != null) {
            seenLabels.addAll(labels);
        }
        List<String> low = lower(labels);

        switch (step) {
            case FIND_PROFILE: {
                if (find(low, REPOST_TABS) != null) {
                    // sudah di profil (tab Repost terlihat)
                    step = Step.REPOST_TAB;
                    attempt = 0;
                    return next(low, now);
                }
                String p = find(low, PROFILE_TABS);
                if (p != null) {
                    log("Buka profil");
                    attempt = 0;
                    step = Step.REPOST_TAB;
                    return new Decision(Action.TAP_PROFILE, p, "buka profil");
                }
                return retryOrAbort(now, "Tab Profil tidak ditemukan");
            }
            case REPOST_TAB: {
                if (hasAny(low, NO_REPOST_HINTS)) {
                    log("Tidak ada repost \u2014 selesai");
                    step = Step.FINISHED;
                    return new Decision(Action.FINISH, null, "tidak ada repost");
                }
                String t = find(low, REPOST_TABS);
                if (t != null) {
                    log("Buka tab Repost");
                    step = Step.GRID;
                    attempt = 0;
                    nextTickAt = now + 1500;
                    return new Decision(Action.TAP_REPOST_TAB, t, "buka tab repost");
                }
                return retryOrAbort(now, "Tab Repost tidak ditemukan");
            }
            case GRID: {
                if (hasAny(low, NO_REPOST_HINTS)) {
                    log("Grid repost kosong \u2014 selesai");
                    step = Step.FINISHED;
                    return new Decision(Action.FINISH, null, "repost habis");
                }
                if (deleted >= maxDeletes) {
                    log("Batas " + maxDeletes + " tercapai");
                    step = Step.FINISHED;
                    return new Decision(Action.FINISH, null, "batas tercapai");
                }
                log("Buka video repost #" + (deleted + 1));
                tappedMenuOnce = false;
                step = Step.MENU;
                attempt = 0;
                nextTickAt = now + 1800;
                return new Decision(Action.TAP_FIRST_ITEM, null, "buka video");
            }
            case MENU: {
                String r = find(low, REMOVE_ACTIONS);
                if (r != null && dialogOpen) {
                    // menu sudah terbuka dan aksi hapus terlihat
                    step = Step.REMOVE;
                    return next(low, now);
                }
                String m = find(low, MENU_TRIGGERS);
                if (m != null && !tappedMenuOnce) {
                    tappedMenuOnce = true;
                    log("Buka menu bagikan");
                    nextTickAt = now + 1200;
                    return new Decision(Action.TAP_MENU, m, "buka menu");
                }
                if (tappedMenuOnce) {
                    if (r != null) {
                        step = Step.REMOVE;
                        return next(low, now);
                    }
                    return retryOrAbort(now, "Menu tidak terbuka / tombol hapus tidak ada");
                }
                return retryOrAbort(now, "Tombol menu tidak ditemukan");
            }
            case REMOVE: {
                String r = find(low, REMOVE_ACTIONS);
                if (r == null) {
                    return retryOrAbort(now, "\"Hapus repost\" tidak ditemukan");
                }
                log("Ketuk hapus repost (" + r + ")");
                step = Step.CONFIRM;
                attempt = 0;
                nextTickAt = now + 900;
                return new Decision(Action.TAP_REMOVE, r, "hapus repost");
            }
            case CONFIRM: {
                if (dialogOpen) {
                    String c = find(low, CONFIRM_ACTIONS);
                    if (c != null) {
                        deleted++;
                        log((dryRun ? "[latihan] " : "") + "Konfirmasi hapus \u2014 total " + deleted);
                        step = Step.GAP;
                        attempt = 0;
                        nextTickAt = now + gapMs;
                        return new Decision(Action.TAP_CONFIRM, c, "konfirmasi");
                    }
                }
                // Tidak ada dialog: mungkin hapus tanpa konfirmasi. Anggap selesai satu item.
                deleted++;
                log((dryRun ? "[latihan] " : "") + "Terhapus tanpa dialog \u2014 total " + deleted);
                step = Step.GAP;
                attempt = 0;
                nextTickAt = now + gapMs;
                return new Decision(Action.WAIT, null, "jeda setelah hapus");
            }
            case GAP: {
                if (deleted >= maxDeletes) {
                    log("Selesai: " + deleted + " repost");
                    step = Step.FINISHED;
                    return new Decision(Action.FINISH, null, "batas tercapai");
                }
                log("Jeda selesai, lanjut video berikutnya");
                step = Step.GRID;
                attempt = 0;
                nextTickAt = now + 500;
                return next(low, now);
            }
            default:
                return new Decision(Action.WAIT, null, lastNote);
        }
    }

    private Decision next(List<String> low, long now) {
        return onTick(low, false, now);
    }

    /** Beri kesempatan beberapa tick sebelum menyerah (UI TikTok butuh waktu render). */
    private Decision retryOrAbort(long now, String note) {
        attempt++;
        lastNote = note;
        if (attempt > 14) {
            log("Berhenti: " + note);
            step = Step.ABORTED;
            return new Decision(Action.ABORT, null, note);
        }
        nextTickAt = now + 600;
        return new Decision(Action.WAIT, null, note + " (" + attempt + "/14)");
    }

    /** Paksa selesai (tombol berhenti). */
    public void stop() {
        if (!done()) {
            log("Dihentikan pengguna pada " + deleted + " repost");
            step = Step.FINISHED;
        }
    }

    private void log(String line) {
        log.add(line);
        while (log.size() > 120) {
            log.remove(0);
        }
        lastNote = line;
    }

    private static List<String> lower(List<String> labels) {
        List<String> out = new ArrayList<>();
        if (labels != null) {
            for (String l : labels) {
                if (l != null) {
                    out.add(l.toLowerCase(Locale.ROOT).trim());
                }
            }
        }
        return out;
    }

    /** Cari label pertama yang cocok (persis, lalu mengandung) — kembalikan label aslinya. */
    static String find(List<String> lowLabels, String[] candidates) {
        for (String c : candidates) {
            for (String l : lowLabels) {
                if (l.equals(c)) {
                    return l;
                }
            }
        }
        for (String c : candidates) {
            for (String l : lowLabels) {
                if (l.length() <= 60 && l.contains(c)) {
                    return l;
                }
            }
        }
        return null;
    }

    static boolean hasAny(List<String> lowLabels, String[] candidates) {
        return find(lowLabels, candidates) != null;
    }
}
