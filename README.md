# Unrepost — penyaring video repost TikTok (APK Android)

APK Android ringan (**±109 KB**, tanpa iklan, tanpa server, tanpa library pihak ketiga) yang
memantau layar TikTok dan **menandai video repost / re-upload** — yaitu video yang bukan milik
akun yang mengunggahnya (caption "repost", "credit: @...", "not mine", "ctto", "bukan video
saya", dan sejenisnya). Opsional: video seperti itu **dilewati otomatis** (swipe ke video
berikutnya).

> ⚠️ Proyek ini **tidak berafiliasi dengan TikTok/ByteDance** dan hanya untuk penggunaan pribadi
> (sideload). Aplikasi tidak mengunduh video, tidak menghapus watermark, tidak login, dan tidak
> mengirim data apa pun ke internet. Semua analisis dilakukan di perangkat.

## 📦 Unduh

| Berkas | Keterangan |
|---|---|
| [`dist/unrepost-debug.apk`](dist/unrepost-debug.apk) | APK siap instal (ditandatangani keystore debug, v1+v2+v3) |

Pasang di Android 7.0+ (minSdk 24). Cara pasang:

1. Pindahkan `unrepost-debug.apk` ke HP (WhatsApp/Drive/kabel USB).
2. Buka filenya → izinkan **"Instal dari sumber tidak dikenal"** untuk aplikasi yang membuka
   APK tersebut (File Manager / Chrome).
3. Buka **Unrepost** → tombol **"Buka pengaturan aksesibilitas"**.
4. Di *Setelan → Aksesibilitas → Aplikasi terinstal* (atau *Layanan yang diunduh*), pilih
   **Unrepost** → aktifkan. Penyaring langsung menyala.
5. Kembali ke Unrepost → tekan **"Buka TikTok"**.

## ✨ Fitur

- **Deteksi repost berbasis teks layar** — mengumpulkan caption, username, mention, stiker teks,
  dan overlay yang terlihat lewat AccessibilityService, lalu memberi skor 0–600.
- **3 tingkat sensitivitas** — Konservatif (hanya penanda jelas), Normal, Agresif (ikut menandai
  atribusi samar seperti `credit:` / `via @` / `ig:`).
- **Badge overlay** — muncul di atas video TikTok saat repost terdeteksi, plus skornya.
- **Lewati otomatis (opsional)** — menjalankan gestur swipe ke jarak aman; otomatis tidak
  menggeser bila terdeteksi sheet komentar / dialog bagikan sedang terbuka.
- **Riwayat repost** — sidik jari (fingerprint) caption + judul + waktu; anti-duplikat, maks 300.
- **Statistik harian & total** — repost terdeteksi vs dilewati.
- **Uji deteksi lokal** — tempel caption dan lihat skor serta alasan penilaiannya.
- **Privasi** — tidak ada akses internet sama sekali (tidak ada izin `INTERNET` di manifest).

## 🧠 Cara kerja deteksi

`app/src/com/unrepost/app/RepostDetector.java` (murni Java, bisa diuji di desktop):

1. Teks layar dinormalisasi (huruf kecil, emoji & tanda baca dibuang, URL/hashtag/mention
   dipisah untuk sidik jari).
2. Pencocokan kamus berlapis:
   - **Kuat (+100)** — `repost`, `re-upload`, `ctto`, `credit to owner`, `dm for credit`,
     `not mine`, `bukan video saya`, `hanya repost`, `sumber:`, `no copyright infringement`, dst.
   - **Sedang (+55)** — pola atribusi: `credit:`, `via @`, `ig:`, `yt:`, `repost from`, …
   - **Lemah (+30)** — `©`, `copyright`, `subtitle by`, `maaf jika`, `sekadar berbagi`, …
   - **Bonus** — mention akun lain (+20), beberapa tanda sedang sekaligus (+25),
     banyak hashtag pada caption ber-atribusi (+15).
   - **Penalti (−25)** — penanda konten orisinal (`pov:`, `resep`, `tutorial`, `aku bikin`, …).
3. `skor ≥ ambang` → **repost**. Ambang: Konservatif 135, Normal 100, Agresif 70.

Uji: `bash tools/test.sh` → **18/18 lulus** (caption Indonesia + internasional, positif & negatif).

## 📱 Batasan yang perlu diketahui (jujur)

- TikTok **tidak** punya label resmi "repost" di UI. Jadi deteksi ini **heuristik berbasis teks**:
  akurat untuk video yang captions-nya memuat penanda repost; **tidak** bisa mengenali repost
  yang caption-nya dihapus total atau videonya tanpa teks sama sekali.
- Tidak ada pembacaan audio/video → tidak ada pendeteksian duplikat visual.
- Gestur swipe otomatis bergantung pada tata letak TikTok yang sedang dipakai; aplikasi sengaja
  memakai jarak geser konservatif agar tidak "nyangkut" di kolom komentar. Bila TikTok berubah
  desain, opsi lewati otomatis bisa dimatikan dan badge tetap bekerja.
- Butuh izin Aksesibilitas — itulah satu-satunya cara membaca teks aplikasi lain. Karena itu pula
  APK ini untuk sideload, bukan Play Store.

## 🛠️ Build dari sumber

Toolchain di sandbox/CI: **aapt2 → ecj → d8 → apksigner** (tanpa Gradle & tanpa Android SDK penuh).

```bash
bash tools/fetch-toolchain.sh   # unduh JDK(JRE), aapt2, ecj, d8, apksigner, android.jar ke .work/tc
bash tools/test.sh              # unit test mesin deteksi (JVM biasa)
bash tools/build-apk.sh         # → dist/unrepost-debug.apk
bash tools/build-apk.sh --release   # keystore sendiri di keys/ → dist/unrepost-release.apk
```

**Android Studio** juga bisa: buka folder ini, `app/build.gradle` memakai `src/`, `res/`, dan
`AndroidManifest.xml` apa adanya → Sync → *Build APK*.

**GitHub Actions**: `.github/workflows/build-apk.yml` menjalankan test + build dan mengunggah
artifact APK pada setiap push ke `main` (atau lewat *Run workflow*).

## 🗂 Struktur

```
app/
  AndroidManifest.xml          # activity + AccessibilityService (tanpa izin INTERNET)
  res/                         # tema, warna, ikon launcher, konfigurasi layanan
  src/com/unrepost/app/
    MainActivity.java          # UI utama: status, setelan, uji caption, statistik
    UnrepostService.java       # AccessibilityService: scan layar, badge, swipe otomatis
    RepostDetector.java        # mesin skor repost (pure Java)
    RepostLog.java             # riwayat (FIFO 300, anti-duplikat)
    Prefs.java                 # setelan & statistik
    HistoryActivity.java       # daftar repost yang ditandai
tools/
  fetch-toolchain.sh  build-apk.sh  test.sh  lambda-stub/
tools/test/com/unrepost/app/DetectorTest.java
dist/unrepost-debug.apk
```

## 📄 Lisensi

MIT — lihat [LICENSE](LICENSE). Nama, merek, dan logo TikTok adalah milik ByteDance; ikon di
proyek ini hanya ilustrasi gaya bebas untuk aplikasi pihak ketiga.
