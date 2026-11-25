package com.unrepost.app;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Mesin deteksi video repost / re-upload TikTok.
 *
 * <p>Mesin ini murni logika (tanpa dependensi Android) supaya bisa diuji di desktop.
 * Masukannya adalah teks yang terlihat di layar TikTok: caption, nama kreator,
 * username, stiker teks, dan potongan komentar/overlay.</p>
 *
 * <p>Skor 0..120+. Ambang bawaan 100 (dapat diatur).</p>
 */
public final class RepostDetector {

    /** Penanda kuat: satu temuan sudah cukup untuk menyimpulkan repost. */
    private static final String[] STRONG = {
            "repost", "re-post", "reposting", "re upload", "re-upload", "reupload",
            "reunggah", "unggah ulang", "ulang unggah", "republikasi", "republish",
            "no copyright infringement", "not copyright infringement",
            "credit to owner", "credit to the owner", "ctto", "ctto:", "dm for credit",
            "dm for removal", "not mine", "bukan video saya", "bukan vidio saya",
            "bukan karya saya", "hanya repost", "cuma repost", "cuma numpang lewat",
            "if you know the owner", "if you own this", "video ini bukan milik saya",
            "sumber video", "sumber:", "source video", "video source",
            "original video by", "original creator", "kreator asli",
            "follow the original", "follow kreator asli", "support the original",
            "jangan lupa follow kreator", "i dont own", "i don't own", "i do not own",
            "no copyright intended", "for entertainment only", "for educational purposes only"
    };

    /** Penanda sedang: butuh dua temuan, atau satu temuan + sinyal pendukung. */
    private static final String[] MEDIUM = {
            "credit:", "credit @", "credit -", "credits:", "vid by", "video by",
            "ig:", "ig @", "instagram:", "insta:", "yt:", "youtube:", "tw:", "twitter:",
            "tiktok:", "tiktok @", "via @", "via:", "repost from", "from @",
            "kolaborasi dengan", "collab with", "bersama @", "dipopulerkan oleh"
    };

    /** Sinyal lemah: hanya menaikkan skor bila sudah ada sinyal lain. */
    private static final String[] WEAK = {
            "\u00a9", "copyright", "watermark", "subtitle by", "sub by", "sub Indo by",
            "like & follow", "like and follow", "jangan lupa like", "mohon maaf jika",
            "maaf jika", "bukan bermaksud", "tidak bermaksud", "sekadar berbagi",
            "sekedar berbagi", "hanya berbagi", "just sharing", "sharing is caring",
            "bantu viralkan", "viralin", "mohon ijin", "minta ijin", "izin repost",
            "izin reupload", "izin upload", "with permission"
    };

    /** Tanda caption asli (menurunkan kecurigaan). */
    private static final String[] ORIGINAL_HINTS = {
            "pov:", "pov ", "day 1", "hari ke", "tutorial", "resep", "menurutku",
            "aku bikin", "aku buat", "saya bikin", "saya buat", "i made", "i built",
            "my project", "proyekku", "karyaku", "my art", "my recipe"
    };

    public static final int THRESHOLD_HIGH_SENSITIVITY = 70;
    public static final int THRESHOLD_NORMAL = 100;
    public static final int THRESHOLD_CONSERVATIVE = 135;

    private RepostDetector() {
    }

    /** Hasil analisis satu layar. */
    public static final class Result {
        public boolean isRepost;
        public int score;
        public int threshold;
        public final List<String> reasons = new ArrayList<>();
        public String signature = "";
        public String title = "";

        public String reasonText() {
            if (reasons.isEmpty()) {
                return "Tidak ada penanda repost.";
            }
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < reasons.size(); i++) {
                if (i > 0) {
                    sb.append(" \u2022 ");
                }
                sb.append(reasons.get(i));
            }
            return sb.toString();
        }
    }

    /**
     * Analisis teks layar.
     *
     * @param rawText  semua teks yang terlihat, dipisah newline
     * @param threshold ambang skor (mis. {@link #THRESHOLD_NORMAL})
     */
    public static Result analyze(String rawText, int threshold) {
        Result r = new Result();
        r.threshold = threshold;
        if (rawText == null) {
            rawText = "";
        }
        String text = " " + rawText.toLowerCase(Locale.ROOT)
                .replace('\u2019', '\'')
                .replace('\u2018', '\'')
                .replace('\u00a0', ' ')
                .replaceAll("[\\p{Cntrl}&&[^\\n]]", " ")
                .replaceAll("[ \\t]+", " ")
                + " ";

        int strong = 0;
        int medium = 0;
        int weak = 0;

        for (String k : STRONG) {
            if (text.contains(k)) {
                strong++;
                r.score += 100;
                addReason(r, "penanda repost: \"" + k.trim() + "\"");
            }
        }
        for (String k : MEDIUM) {
            if (text.contains(k)) {
                medium++;
                r.score += 55;
                addReason(r, "atribusi kreator: \"" + k.trim() + "\"");
            }
        }
        for (String k : WEAK) {
            if (text.contains(k)) {
                weak++;
                r.score += 30;
                addReason(r, "sinyal lemah: \"" + k.trim() + "\"");
            }
        }

        // Kombinasi: satu medium + satu weak cukup mendekati ambang; dua medium naik tajam.
        if (medium >= 2) {
            r.score += 25;
        }
        if (strong == 0 && medium >= 1 && weak >= 1) {
            r.score += 20;
        }
        // Atribusi ke akun lain (mention) memperkuat dugaan repost.
        if (medium >= 1 && text.matches("(?s).*@[a-z0-9._]{1,}.*")) {
            r.score += 20;
            addReason(r, "menyebut akun kreator asli");
        }

        for (String k : ORIGINAL_HINTS) {
            if (text.contains(k)) {
                r.score -= 25;
                addReason(r, "petunjuk konten asli: \"" + k.trim() + "\"");
                break;
            }
        }

        // Banyak hashtag + rangkaian teks promosi = pola khas akun re-upload.
        int hashtags = countHashtags(text);
        if (hashtags >= 6 && (medium > 0 || strong > 0)) {
            r.score += 15;
            addReason(r, hashtags + " hashtag pada caption");
        }

        if (r.score < 0) {
            r.score = 0;
        }
        r.isRepost = r.score >= threshold;
        r.signature = signature(rawText);
        r.title = title(rawText);
        return r;
    }

    public static Result analyze(String rawText) {
        return analyze(rawText, THRESHOLD_NORMAL);
    }

    /** Buang emoji & simbol, rapikan spasi, huruf kecil. */
    public static String normalize(String s) {
        if (s == null) {
            return "";
        }
        String n = Normalizer.normalize(s, Normalizer.Form.NFKC);
        StringBuilder sb = new StringBuilder(n.length());
        for (int i = 0; i < n.length(); i++) {
            char c = n.charAt(i);
            if (Character.isHighSurrogate(c)) {
                // lewati pasangan surrogate (emoji)
                if (i + 1 < n.length() && Character.isLowSurrogate(n.charAt(i + 1))) {
                    i++;
                }
                sb.append(' ');
                continue;
            }
            if (Character.isLowSurrogate(c)) {
                continue;
            }
            if (Character.isLetterOrDigit(c) || Character.isWhitespace(c)) {
                sb.append(c);
            } else {
                sb.append(' ');
            }
        }
        return sb.toString().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
    }

    /** Teks inti untuk sidik jari: buang hashtag, mention, dan URL. */
    public static String canonical(String raw) {
        if (raw == null) {
            return "";
        }
        String s = raw;
        // hapus URL
        s = s.replaceAll("(?i)https?://\\S+", " ");
        s = s.replaceAll("(?i)www\\.\\S+", " ");
        // hapus token hashtag / mention / emoji-only
        StringBuilder sb = new StringBuilder(s.length());
        for (String tok : s.split("\\s+")) {
            if (tok.isEmpty()) {
                continue;
            }
            char c = tok.charAt(0);
            if (c == '#' || c == '@') {
                continue;
            }
            sb.append(tok).append(' ');
        }
        return normalize(sb.toString());
    }

    /** Sidik jari pendek & stabil dari caption inti. */
    public static String signature(String raw) {
        String core = canonical(raw);
        if (core.length() > 160) {
            core = core.substring(0, 160);
        }
        if (core.isEmpty()) {
            return "";
        }
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            byte[] d = md.digest(core.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 6; i++) {
                sb.append(String.format("%02x", d[i]));
            }
            return sb.toString();
        } catch (Exception e) {
            return Integer.toHexString(core.hashCode());
        }
    }

    /** Potongan teks yang bisa dibaca manusia untuk daftar riwayat. */
    public static String title(String raw) {
        if (raw == null) {
            return "";
        }
        String best = "";
        for (String line : raw.split("\\n")) {
            String t = normalizeKeepCase(line);
            if (t.length() > best.length() && !isChrome(t)) {
                best = t;
            }
        }
        if (best.isEmpty()) {
            best = normalizeKeepCase(raw);
        }
        best = best.replaceAll("\\s+", " ").trim();
        if (best.length() > 90) {
            best = best.substring(0, 87) + "\u2026";
        }
        return best;
    }

    private static String normalizeKeepCase(String s) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (Character.isHighSurrogate(c)) {
                if (i + 1 < s.length() && Character.isLowSurrogate(s.charAt(i + 1))) {
                    i++;
                }
                sb.append(' ');
            } else if (Character.isLetterOrDigit(c)) {
                sb.append(c);
            } else if (Character.isWhitespace(c) || c == '\'' || c == ',' || c == '.' || c == '?'
                    || c == '!' || c == '-') {
                sb.append(c);
            } else {
                sb.append(' ');
            }
        }
        return sb.toString().trim();
    }

    static boolean isChrome(String s) {
        String l = s.toLowerCase(Locale.ROOT);
        return l.equals("tiktok") || l.equals("beranda") || l.equals("for you")
                || l.equals("following") || l.equals("mengikuti") || l.equals("ikuti")
                || l.equals("profil") || l.equals("profile") || l.equals("cari")
                || l.equals("search") || l.equals("inbox") || l.equals("kotak masuk")
                || l.equals("komentar") || l.equals("comments") || l.equals("bagikan")
                || l.equals("share") || l.equals("suka") || l.equals("like")
                || l.equals("rep - buat") || l.equals("buat") || l.equals("create")
                || l.equals("salin tautan") || l.equals("simpan video")
                || l.equals("tambah ke favorit") || l.equals("original sound")
                || l.equals("suara asli");
    }

    private static int countHashtags(String lowered) {
        int c = 0;
        for (int i = 0; i < lowered.length(); i++) {
            if (lowered.charAt(i) == '#') {
                c++;
            }
        }
        return c;
    }

    private static void addReason(Result r, String reason) {
        if (r.reasons.size() < 4 && !r.reasons.contains(reason)) {
            r.reasons.add(reason);
        }
    }
}
