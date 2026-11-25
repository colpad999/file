# Unrepost — penyaring video repost TikTok (APK Android)

APK Android ringan (**±129 KB**, tanpa iklan, tanpa server, tanpa library pihak ketiga) yang
memantau layar TikTok dan **menandai video repost / re-upload** — yaitu video yang bukan milik
akun yang mengunggahnya (caption "repost", "credit: @...", "not mine", "ctto", "bukan video
saya", dan sejenisnya). Opsional: video seperti itu **dilewati otomatis** (swipe ke video
berikutnya).

> ⚠️ Proyek ini **tidak berafiliasi dengan TikTok/ByteDance** dan hanya untuk penggunaan pribadi
> (sideload). Aplikasi tidak mengunduh video, tidak menghapus watermark, tidak login, dan tidak
> mengirim data apa pun ke internet. Semua analisis dilakukan di perangkat.

## 🗑️ Fitur utama: "Bersihkan Repost" (hapus otomatis, satu klik)

Menghapus repost dari **profil TikTok Anda sendiri** secara otomatis dan bertahap:

```
Buka Profil → tab Repost → video pertama → menu Bagikan → "Hapus repost"
→ konfirmasi → JEDA (default 8 detik, bisa 3–40 detik) → video berikutnya …
```

- **Satu klik "Mulai"** — setelah izin aktif, aplikasi membuka TikTok sendiri lalu bekerja sendiri.
- **Bertahap/perlahan** — satu video per jeda, ada batas jumlah (1–100), tombol **Berhenti** kapan
  saja, dan badge progres kecil di atas layar (`HAPUS 7/25 · langkah 4/7`).
- **Mode latihan** — jalankan dulu tanpa menghapus untuk memastikan urutan tombol cocok dengan
  versi TikTok Anda.
- **Berhenti otomatis** — bila grid Repost kosong ("Belum ada repost"), batas tercapai, atau layar
  tidak dikenali selama 14 percobaan (supaya tidak salah ketuk).
- **Log lengkap** — setiap langkah dicatat di layar *Bersihkan Repost* untuk diagnosa.

**Yang perlu dipahami (jujur):**

| Pertanyaan | Jawaban |
|---|---|
| Apakah APK login ke akun TikTok? | **Tidak perlu.** APK ini bekerja pada **aplikasi TikTok yang sudah login di HP Anda** — jadi tidak ada sandi yang diminta/disimpan. Lebih aman: tidak ada data akun yang dikirim ke mana pun. |
| Kenapa lewat aksesibilitas? | Karena TikTok tidak menyediakan API hapus-repost untuk publik, satu-satunya cara otomatis adalah menekan tombol UI-nya. Izin ini tetap harus Anda nyalakan sendiri. |
| Berapa cepat? | Sengaja lambat (default 8 detik/video, disarankan 10–15 detik). Otomasi berkecepatan tinggi berisiko ditandai bot oleh TikTok. |
| Apakah bisa gagal? | Bisa, bila TikTok mengubah nama tombol/menu. Itu sebabnya ada mode latihan + log; label kamus mudah diperbarui di `CleanerEngine.java`. |
| Apakah aman untuk akun? | Hanya menyentuh tab **Repost di profil Anda** (tidak pernah feed For You), berhenti bila UI tidak dikenali, dan tidak menghapus video unggahan Anda. Tetap gunakan jeda wajar & batch kecil. |

## 📦 Unduh APK (langsung, bukan lewat Actions)

| Berkas | Keterangan |
|---|---|
| [`dist/unrepost-debug.apk`](dist/unrepost-debug.apk) | **APK siap instal — klik lalu tombol Download** (v2.0, 129 KB, signed v1+v2+v3) |

APK ini sudah jadi dan tersimpan di repo, jadi **tidak perlu** menunggu atau menjalankan
GitHub Actions. (Workflow Actions hanya untuk membangun ulang otomatis jika kode diubah.)

### Pemasangan + aktivasi (2 ketukan setelah instal)

> Untuk **hapus repost otomatis**: buka Unrepost → **🗑 Buka pembersih repost** → Atur jumlah &
> jeda → **Mulai**. Aplikasi membuka TikTok dan bekerja sendiri. (Izin aksesibilitas tetap harus
> Anda nyalakan sekali — lihat penjelasan di bagian fitur di atas.)

1. Buka `dist/unrepost-debug.apk` di HP → **Download** → buka filenya → izinkan
   **"Instal dari sumber tidak dikenal"** (untuk File Manager/Chrome).
2. Buka **Unrepost** → ketuk **"Aktifkan (1 ketukan)"** → halaman *Setelan → Aksesibilitas →
   Unrepost* langsung terbuka → ketuk **Unrepost** → **Izinkan**.
3. Kembali ke Unrepost → ketuk **"Buka TikTok"**. Penyaring langsung memantau feed.
4. Di TikTok, ketuk **"Pindai layar sekarang"** di Unrepost (atau buka-tutup cepat) untuk memaksa
   pemeriksaan video yang sedang tampil — badge ⛔ / ✓ muncul di atas video.

> **Kenapa izin Aksesibilitas tidak bisa dilewati?** Itu izin sistem paling sensitif di Android:
> hanya pengguna yang boleh mengaktifkannya, aplikasi (termasuk APK ini) secara sengaja
> **tidak diizinkan** menyalakannya sendiri — kalau bisa, itu namanya malware. Jadi maksimum
> yang bisa dilakukan aplikasi adalah membuka halaman izin yang tepat + menampilkan jendela
> "Izinkan" sistem. Selain itu, tanpa izin ini tidak ada cara membaca caption TikTok sama sekali.

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
- **Pindai layar sekarang** — tombol untuk memaksa pemeriksaan layar yang sedang tampil
  (badge ⛔ repost / ✓ aman + skornya), tanpa menunggu event.
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

## 🧪 Test

```bash
bash tools/test.sh
# DetectorTest       : 18/18  (deteksi repost dari caption)
# CleanerEngineTest  : 21/21  (alur hapus: profil, tab repost, menu, konfirmasi, jeda,
#                              batas, mode latihan, berhenti saat UI tak dikenal)
```

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
Ini **opsional** — APK final sudah tersedia di `dist/` dan bisa diunduh langsung dari repo.

## 🗂 Struktur

```
app/
  AndroidManifest.xml          # activity + AccessibilityService (tanpa izin INTERNET)
  res/                         # tema, warna, ikon launcher, konfigurasi layanan
  src/com/unrepost/app/
    MainActivity.java          # UI utama: status, setelan, uji caption, statistik
    UnrepostService.java       # AccessibilityService: scan layar, badge, swipe otomatis
    RepostDetector.java        # mesin skor repost (pure Java)
    CleanerEngine.java         # state machine hapus repost bertahap (pure Java)
    CleanerService.java        # layanan aksesibilitas #2: menjalankan ketukan otomatis
    CleanerActivity.java       # UI "Bersihkan Repost": setelan, progres, log
    CleanerState.java          # progres & log lintas layar
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
