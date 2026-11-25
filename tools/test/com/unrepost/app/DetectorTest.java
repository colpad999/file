package com.unrepost.app;

/**
 * Uji mesin deteksi di JVM (tanpa Android). Jalankan lewat tools/test.sh.
 * Contoh nyata caption TikTok di Indonesia & internasional.
 */
public final class DetectorTest {

    private static int pass = 0;
    private static int fail = 0;

    public static void main(String[] args) {
        // ---- harus REPOST
        repost("@kreatorasli video keren\nrepost dari @someone\n#fyp #viral");
        repost("credit: @original_creator\nno copyright infringement intended");
        repost("bukan video saya, cuma numpang lewat \uD83D\uDE05\n#fyp");
        repost("Repost from TikTok @user1234\nfollow the original creator!");
        repost("dm for credit / dm for removal\nctto");
        repost("hanya repost guys, bukan karya saya\njangan lupa follow kreator asli");
        repost("this is not mine\noriginal video by @someoneelse");

        // ---- harus AMAN (konten asli)
        safe("POV: kamu bangun jam 5 pagi dan masak nasi goreng\n#masak #resep");
        safe("aku bikin kue lapis tiga warna, resepnya di komentar\n#baking");
        safe("day 1 of learning piano\nwish me luck");
        safe("harga tiket konser naik lagi, menurutku kemahalan\n#opinion");
        safe("Tutorial edit video pakai CapCut, dari nol sampai jadi");

        // ---- sidik jari harus stabil terhadap perbedaan format
        String a = RepostDetector.signature("Repost dari @user  #fyp https://tiktok.com/x");
        String b = RepostDetector.signature("repost   dari @user #viral");
        check("signature: hashtag/URL/mention diabaikan", a.equals(b) && !a.isEmpty());
        check("signature: beda caption = beda sidik jari",
                !RepostDetector.signature("halo dunia").equals(RepostDetector.signature("halo bumi")));
        check("signature: emoji tidak mengubah sidik jari",
                RepostDetector.signature("repost \uD83D\uDE05\uD83D\uDD25")
                        .equals(RepostDetector.signature("repost")));
        check("normalize: huruf kecil & tanpa tanda baca",
                RepostDetector.normalize("Halo, DUNIA!!").equals("halo dunia"));
        check("ambang lebih tinggi menurunkan skor yang sama",
                RepostDetector.analyze("credit: @x #fyp", RepostDetector.THRESHOLD_CONSERVATIVE).isRepost
                        == false);
        check("agresif menandai atribusi samar",
                RepostDetector.analyze("credit: @x #fyp", RepostDetector.THRESHOLD_HIGH_SENSITIVITY).isRepost);

        System.out.println("\n=== " + pass + " lulus, " + fail + " gagal ===");
        if (fail > 0) {
            System.exit(1);
        }
    }

    private static void repost(String text) {
        RepostDetector.Result r = RepostDetector.analyze(text);
        check("REPOST  (" + r.score + ") " + oneLine(text), r.isRepost);
    }

    private static void safe(String text) {
        RepostDetector.Result r = RepostDetector.analyze(text);
        check("AMAN    (" + r.score + ") " + oneLine(text), !r.isRepost);
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

    private static String oneLine(String s) {
        String t = s.replace('\n', ' ');
        return t.length() > 58 ? t.substring(0, 57) + "\u2026" : t;
    }
}
