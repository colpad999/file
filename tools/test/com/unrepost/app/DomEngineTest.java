package com.unrepost.app;

import java.util.ArrayList;
import java.util.List;

/**
 * Uji mesin otomasi DOM dengan HTML tiruan halaman TikTok.
 * Skenario: buka halaman repost → grid → video → menu → "Hapus repost" → konfirmasi →
 * kembali → video berikutnya; plus kasus halaman login, menu tanpa aksi, dan batas batch.
 */
public final class DomEngineTest {

    private static int pass;
    private static int fail;
    private static long clock = 5_000_000L;

    public static void main(String[] args) {
        happyPath();
        stopsAtBatchLimit();
        abortsWhenNotLoggedIn();
        recoversWhenMenuHasNoRemoveAction();
        dryRunStillWalksThrough();
        spaTabsKeepWorking();
        emptyUsernameNeverBuildsBadUrl();

        System.out.println("\n=== " + pass + " lulus, " + fail + " gagal ===");
        if (fail > 0) {
            System.exit(1);
        }
    }

    // ------------------------------------------------------------------ page

    private static String gridPage() {
        return "<html><body>"
                + "<div role=\"tablist\"><span role=\"tab\">Video</span>"
                + "<span role=\"tab\" data-ur=\"10\">Repost</span></div>"
                + "<div class=\"grid\">"
                + "<a data-ur=\"20\" href=\"/@aku/video/1001\"><img alt=\"v1\"></a>"
                + "<a data-ur=\"21\" href=\"/@aku/video/1002\"><img alt=\"v2\"></a>"
                + "<a data-ur=\"22\" href=\"/@aku/video/1003\"><img alt=\"v3\"></a>"
                + "</div></body></html>";
    }

    private static String videoPage() {
        return "<html><body><div id=\"main\">"
                + "<video src=\"v.mp4\"></video>"
                + "<div class=\"actions\">"
                + "<button data-e2e=\"share-icon\" data-ur=\"30\" aria-label=\"Bagikan\">Share</button>"
                + "</div></div></body></html>";
    }

    private static String menuPage(boolean withRemove) {
        return videoPage()
                + "<div role=\"dialog\"><ul>"
                + "<li><button data-ur=\"40\">Simpan video</button></li>"
                + (withRemove ? "<li><button data-ur=\"41\">Hapus repost</button></li>" : "")
                + "<li><button data-ur=\"42\">Salin tautan</button></li>"
                + "</ul></div>";
    }

    private static String confirmPage() {
        return videoPage()
                + "<div role=\"dialog\"><p>Hapus repost ini?</p>"
                + "<button data-ur=\"50\">Batal</button>"
                + "<button data-ur=\"51\">Hapus</button></div>";
    }

    private static String loginPage() {
        return "<html><body><form><input type=\"text\" name=\"username\">"
                + "<input type=\"password\" name=\"password\">"
                + "<button>Masuk</button></form></body></html>";
    }

    // ------------------------------------------------------------- skenario

    private static void happyPath() {
        DomEngine e = new DomEngine(3, 2000, false);
        e.setUsername("akunku");

        // halaman awal: halaman TikTok lain (bukan profil) → harus membuka profil
        DomEngine.Decision d = tick(e, gridPage(), "https://www.tiktok.com/foryou", false);
        check("start: buka halaman repost", d.action == DomEngine.Action.OPEN_REPOSTS
                && d.sel.contains("@akunku"), String.valueOf(d));
        // sudah di profil sendiri → jangan reload (menghindari dilempar ke login)
        DomEngine e2 = new DomEngine(3, 2000, false);
        e2.setUsername("akunku");
        DomEngine.Decision d2 = tick(e2, gridPage(), "https://www.tiktok.com/@akunku", false);
        check("start: tidak reload bila sudah di profil", d2.action == DomEngine.Action.WAIT,
                String.valueOf(d2));

        d = tick(e, gridPage(), "https://www.tiktok.com/@akunku", false);
        check("grid: ketuk tab Repost", d.action == DomEngine.Action.TAP_NODE && "10".equals(d.sel),
                String.valueOf(d));
        wait(e);

        d = tick(e, gridPage(), "https://www.tiktok.com/@akunku", false);
        check("grid: buka video pertama", d.action == DomEngine.Action.TAP_NODE
                && "20".equals(d.sel), String.valueOf(d));
        wait(e);

        d = tick(e, videoPage(), "https://www.tiktok.com/@akunku/video/1001", false);
        check("video: ketuk tombol bagikan/menu", d.action == DomEngine.Action.TAP_MENU
                && "30".equals(d.sel), String.valueOf(d));
        wait(e);

        d = tick(e, menuPage(true), "https://www.tiktok.com/@akunku/video/1001", false);
        check("menu: temukan 'Hapus repost'", d.action == DomEngine.Action.TAP_REMOVE
                && "41".equals(d.sel), String.valueOf(d));
        wait(e);

        d = tick(e, confirmPage(), "https://www.tiktok.com/@akunku/video/1001", false);
        check("konfirmasi: ketuk 'Hapus'", d.action == DomEngine.Action.TAP_CONFIRM
                && "51".equals(d.sel), String.valueOf(d));
        check("konfirmasi: belum dihitung sebelum diklik", e.deleted() == 0, "deleted=" + e.deleted());

        d = tick(e, videoPage(), "https://www.tiktok.com/@akunku/video/1001", false);
        check("setelah diklik: tunggu sebentar", d.action == DomEngine.Action.WAIT, String.valueOf(d));
        wait(e);
        d = tick(e, videoPage(), "https://www.tiktok.com/@akunku/video/1001", false);
        check("setelah hapus: hitungan naik jadi 1", e.deleted() == 1, "deleted=" + e.deleted());
        check("setelah hapus: kembali ke grid", d.action == DomEngine.Action.GO_BACK, String.valueOf(d));
        check("progres menyebut langkah", e.progressText().contains("langkah"), e.progressText());
        check("log berisi aksi", e.logLines().size() >= 4, "log=" + e.logLines());
    }

    private static void stopsAtBatchLimit() {
        DomEngine e = new DomEngine(1, 2000, false);
        e.setUsername("a");
        tick(e, gridPage(), "u", false); wait(e);
        tick(e, gridPage(), "u", false); wait(e);
        tick(e, gridPage(), "u", false); wait(e);
        tick(e, videoPage(), "u", false); wait(e);
        tick(e, menuPage(true), "u", false); wait(e);
        tick(e, confirmPage(), "u", false);
        tick(e, videoPage(), "u", false);
        wait(e);
        DomEngine.Decision d = tick(e, videoPage(), "u", false);
        check("batas: 1 terhapus", e.deleted() == 1, "deleted=" + e.deleted());
        check("batas: langsung selesai", d.action == DomEngine.Action.FINISH || e.done(),
                String.valueOf(d));
    }

    private static void abortsWhenNotLoggedIn() {
        DomEngine e = new DomEngine(5, 2000, false);
        e.setUsername("a");
        tick(e, gridPage(), "u", false);
        DomEngine.Decision d = tick(e, loginPage(), "https://www.tiktok.com/login", false);
        check("halaman login: berhenti, tidak menghapus", d.action == DomEngine.Action.ABORT
                && e.deleted() == 0, String.valueOf(d));
    }

    private static void recoversWhenMenuHasNoRemoveAction() {
        DomEngine e = new DomEngine(3, 2000, false);
        e.setUsername("a");
        tick(e, gridPage(), "u", false); wait(e);
        tick(e, gridPage(), "u", false); wait(e);
        tick(e, videoPage(), "u", false); wait(e);
        DomEngine.Decision d = tick(e, menuPage(false), "u", false);
        check("menu tanpa 'hapus repost': coba buka menu lagi",
                d.action == DomEngine.Action.TAP_MENU, String.valueOf(d));
        // beri waktu, akhirnya harus menyerah (ABORT) bukan diam selamanya
        boolean abort = false;
        for (int i = 0; i < 40 && !e.done(); i++) {
            clock += 2000;
            DomEngine.Decision x = e.onTick(menuPage(false), "u", clock, false);
            if (x.action == DomEngine.Action.ABORT || x.action == DomEngine.Action.FINISH) {
                abort = true;
                break;
            }
        }
        check("menu tanpa aksi: akhirnya berhenti sendiri", abort);
        check("menu tanpa aksi: tidak ada yang terhapus", e.deleted() == 0);
    }

    private static void dryRunStillWalksThrough() {
        DomEngine e = new DomEngine(2, 2000, true);
        e.setUsername("a");
        check("latihan: label progres", e.progressText().startsWith("LATIHAN"), e.progressText());
        tick(e, gridPage(), "u", false); wait(e);
        tick(e, gridPage(), "u", false); wait(e);
        tick(e, videoPage(), "u", false); wait(e);
        tick(e, menuPage(true), "u", false); wait(e);
        tick(e, confirmPage(), "u", false);
        tick(e, confirmPage(), "u", false);
        wait(e);
        tick(e, videoPage(), "u", false);
        check("latihan: tetap mencatat langkah", e.deleted() == 1 && e.logLines().size() > 3,
                "deleted=" + e.deleted() + " log=" + e.logLines().size());
    }

    /** TikTok SPA: setelah klik tab, DOM berganti tanpa reload — mesin harus lanjut. */
    /** Tanpa nama akun, mesin tidak boleh membuat URL "https://www.tiktok.com/@" (dilempar ke login). */
    private static void emptyUsernameNeverBuildsBadUrl() {
        check("url profil: username kosong -> kosong", DomEngine.repostUrl("").isEmpty());
        check("url profil: '@' saja -> kosong", DomEngine.repostUrl("@").isEmpty());
        check("url profil: null -> kosong", DomEngine.repostUrl(null).isEmpty());
        check("url profil: normal -> benar",
                "https://www.tiktok.com/@aku?lang=id".equals(DomEngine.repostUrl("@aku")));

        DomEngine e = new DomEngine(2, 2000, false);   // username tidak di-set
        DomEngine.Decision d = tick(e, gridPage(), "https://www.tiktok.com/@aku", false);
        check("tanpa username: tidak membuka URL profil, pakai halaman terbuka",
                d.action == DomEngine.Action.WAIT, String.valueOf(d));
        // halaman login palsu (ada kolom sandi) harus dikenali
        String login = "<html><body><form><input type=\"text\" name=\"username\">"
                + "<input type=\"password\" name=\"password\"><button>Masuk</button></form></body></html>";
        DomEngine e3 = new DomEngine(2, 2000, false);
        tick(e3, gridPage(), "u", false);
        DomEngine.Decision d3 = tick(e3, login, "https://www.tiktok.com/login", false);
        check("halaman login dikenali dari kolom sandi", d3.action == DomEngine.Action.ABORT,
                String.valueOf(d3));
    }

    private static void spaTabsKeepWorking() {
        DomEngine e = new DomEngine(2, 2000, false);
        e.setUsername("aku");
        tick(e, gridPage(), "https://www.tiktok.com/@aku", false); wait(e);
        DomEngine.Decision d = tick(e, gridPage(), "https://www.tiktok.com/@aku", false);
        check("spa: tab repost diklik", d.action == DomEngine.Action.TAP_NODE, String.valueOf(d));
        wait(e);
        // konten berubah (tanpa reload) → snapshot baru
        String repostTab = "<html><body><div role=\"tablist\">"
                + "<span role=\"tab\">Video</span>"
                + "<span role=\"tab\" aria-selected=\"true\" data-ur=\"10\">Repost</span></div>"
                + "<a data-ur=\"20\" href=\"/@aku/video/9\"><img alt=\"x\"></a></body></html>";
        d = tick(e, repostTab, "https://www.tiktok.com/@aku", false);
        check("spa: lanjut ke video setelah DOM berganti",
                d.action == DomEngine.Action.TAP_NODE && "20".equals(d.sel), String.valueOf(d));
    }

    // ------------------------------------------------------------------ util

    private static DomEngine.Decision tick(DomEngine e, String html, String url, boolean loading) {
        clock += 3000;
        return e.onTick(html, url, clock, loading);
    }

    private static void wait(DomEngine e) {
        clock += 4000;
    }

    private static void check(String name, boolean ok) {
        check(name, ok, null);
    }

    private static void check(String name, boolean ok, String extra) {
        if (ok) {
            pass++;
            System.out.println("  ok   " + name);
        } else {
            fail++;
            System.out.println("  FAIL " + name + (extra == null ? "" : "  → " + extra));
        }
    }
}
