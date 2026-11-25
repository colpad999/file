package com.unrepost.app;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Uji transisi mesin "Bersihkan Repost" tanpa Android.
 * Mensimulasikan layar TikTok: profil, tab repost, sheet bagikan, dialog konfirmasi.
 */
public final class CleanerEngineTest {

    private static int pass;
    private static int fail;
    private static long clock = 1_000_000L;

    public static void main(String[] args) {
        happyPath();
        dryRunCountsWithoutDeleting();
        noRepostsEndsImmediately();
        stopsAtMaxDeletes();
        abortsWhenUiUnrecognized();
        userStopEndsCleanly();

        System.out.println("\n=== " + pass + " lulus, " + fail + " gagal ===");
        if (fail > 0) {
            System.exit(1);
        }
    }

    // ------------------------------------------------------------------ skenario

    private static void happyPath() {
        CleanerEngine e = new CleanerEngine(3, 3000, false);
        // 1) layar awal: navigasi bawah
        CleanerEngine.Decision d = tick(e, labels("For You", "Profil", "Inbox"), false);
        check("1 buka profil", d.action == CleanerEngine.Action.TAP_PROFILE);
        check("target label profil", "profil".equals(d.label));

        // 2) profil terbuka, tab Repost terlihat
        d = tick(e, labels("Profil", "@saya", "Repost", "Suka"), false);
        check("2 buka tab repost", d.action == CleanerEngine.Action.TAP_REPOST_TAB);
        wait(e);

        // 3) grid repost
        d = tick(e, labels("Repost", "12 video"), false);
        check("3 buka video pertama", d.action == CleanerEngine.Action.TAP_FIRST_ITEM);
        wait(e);

        // 4) menu belum terbuka -> ketuk bagikan
        d = tick(e, labels("Bagikan", "Komentar"), false);
        check("4 buka menu", d.action == CleanerEngine.Action.TAP_MENU);
        wait(e);

        // 5) menu terbuka berisi aksi hapus
        d = tick(e, labels("Bagikan ke", "Hapus repost", "Batal"), true);
        check("5 hapus repost", d.action == CleanerEngine.Action.TAP_REMOVE);
        wait(e);

        // 6) dialog konfirmasi
        d = tick(e, labels("Hapus repost ini?", "Hapus", "Batal"), true);
        check("6 konfirmasi", d.action == CleanerEngine.Action.TAP_CONFIRM);
        check("6a hitungan naik", e.deleted() == 1);
        check("6b masuk fase jeda", e.progressText().contains("langkah 7"));
        clock += 3000;
        check("6c masih berjalan setelah jeda", !e.done());
    }

    private static void dryRunCountsWithoutDeleting() {
        CleanerEngine e = new CleanerEngine(2, 3000, true);
        List<String> flow = labels("Profil");
        step(e, flow, false);
        step(e, labels("Profil", "Repost"), false);
        wait(e);
        step(e, labels("Repost"), false);
        wait(e);
        step(e, labels("Bagikan"), false);
        wait(e);
        step(e, labels("Hapus repost"), true);
        CleanerEngine.Decision d = tick(e, labels("Hapus"), true);
        check("latihan: konfirmasi tetap diketuk", d.action == CleanerEngine.Action.TAP_CONFIRM);
        check("latihan: label progres menyebut LATIHAN", e.progressText().startsWith("LATIHAN"));
    }

    private static void noRepostsEndsImmediately() {
        CleanerEngine e = new CleanerEngine(10, 3000, false);
        tick(e, labels("Profil"), false);
        CleanerEngine.Decision d = tick(e, labels("Profil", "Repost", "Belum ada repost"), false);
        check("tanpa repost: langsung selesai", d.action == CleanerEngine.Action.FINISH);
        check("tanpa repost: tidak menghapus", e.deleted() == 0 && e.finished());
    }

    private static void stopsAtMaxDeletes() {
        CleanerEngine e = new CleanerEngine(1, 3000, false);
        tick(e, labels("Profil"), false);
        tick(e, labels("Profil", "Repost"), false);
        wait(e);
        tick(e, labels("Repost"), false);
        wait(e);
        tick(e, labels("Bagikan"), false);
        wait(e);
        tick(e, labels("Hapus repost"), true);
        tick(e, labels("Hapus"), true);
        check("batas: 1 terhapus", e.deleted() == 1);
        clock += 3000;
        CleanerEngine.Decision d = tick(e, labels("Repost"), false);
        check("batas: selesai setelah kuota", d.action == CleanerEngine.Action.FINISH);
        check("batas: state finished", e.finished());
    }

    private static void abortsWhenUiUnrecognized() {
        CleanerEngine e = new CleanerEngine(5, 3000, false);
        CleanerEngine.Decision d = null;
        for (int i = 0; i < 20; i++) {
            d = tick(e, labels("Layar aneh", "Tidak dikenal"), false);
            if (d.action == CleanerEngine.Action.ABORT) {
                break;
            }
        }
        check("layar tak dikenal: berhenti sendiri", d != null && d.action == CleanerEngine.Action.ABORT);
        check("layar tak dikenal: tidak crash & tidak menghapus", e.deleted() == 0);
    }

    private static void userStopEndsCleanly() {
        CleanerEngine e = new CleanerEngine(10, 3000, false);
        tick(e, labels("Profil"), false);
        e.stop();
        check("stop: selesai", e.done() && e.finished());
        check("stop: tidak minus", e.deleted() == 0);
    }

    // ------------------------------------------------------------------ util

    private static CleanerEngine.Decision tick(CleanerEngine e, List<String> labels, boolean dialog) {
        clock += 1000;
        return e.onTick(labels, dialog, clock);
    }

    /** Majukan waktu sampai mesin bisa bertindak lagi (lewati fase jeda). */
    private static void wait_(CleanerEngine e) {
        clock += 4000;
    }

    private static void wait(CleanerEngine e) {
        clock += 4000;
    }

    private static void step(CleanerEngine e, List<String> labels, boolean dialog) {
        tick(e, labels, dialog);
    }

    private static List<String> labels(String... values) {
        return new ArrayList<>(Arrays.asList(values));
    }

    private static void check(String name, boolean ok) {
        if (ok) {
            pass++;
            System.out.println("  ok   " + name);
        } else {
            fail++;
            System.out.println("  FAIL " + name);
        }
    }
}
