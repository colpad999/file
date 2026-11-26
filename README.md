# Unrepost 2.1 — bersihkan repost TikTok lewat website di dalam APK

Sekarang aplikasinya **berupa website** yang tertanam di dalam APK (WebView). Tidak ada yang
perlu diaktifkan di Setelan: buka app → **login TikTok** → tekan **satu tombol** → repost di
profil Anda dihapus satu per satu, perlahan.

```
Login (di halaman Unrepost)  →  🗑 Bersihkan repost sekarang
   ↓
Unrepost baca tab Repost di profil Anda → hapus video #1 → jeda 12 dtk → #2 → … → batch selesai
```

| | |
|---|---|
| **Ukuran APK** | ±146 KB |
| **Izin yang diminta ke pengguna** | tidak ada (hanya INTERNET untuk memanggil API TikTok) |
| **Perlu aktifkan Aksesibilitas?** | **tidak** (mode lama tetap ada sebagai cadangan) |
| **Login** | di halaman login resmi TikTok di dalam app (Google/2FA didukung); sesi disimpan sebagai cookie WebView |
| **Cara menghapus** | otomasi klik di halaman TikTok asli (bukan API internal) |
| **Data dikirim ke server lain?** | tidak ada — hanya ke `*.tiktok.com` |

## 🔥 v2.3 — perbaikan inti: "tidak bisa menghapus repost"

**Kenapa versi lama gagal (jujur):** mode sebelumnya memanggil **API JSON internal TikTok**
(`/api/post/item_list/`, `/api/v1/aweme/digest/undo/repost/`). Sejak 2023 TikTok mewajibkan
tanda tangan **X-Bogus / a_bogus** pada setiap permintaan, sehingga permintaan tanpa tanda tangan
ditolak — daftar repost kosong dan perintah hapus tidak pernah dieksekusi. Itu tidak bisa
diperbaiki dengan parameter tambahan; pendekatan itu memang tidak layak.

**Penggantinya (yang sekarang bekerja):** Unrepost membuka **halaman TikTok asli** di dalam
aplikasi dan menekan tombol «Hapus repost» sungguhan — seperti tangan Anda, hanya otomatis:

```
Halaman profil → tab Repost → video #1 → tombol Bagikan/menu → "Hapus repost" → konfirmasi
      → jeda 12 dtk → video berikutnya …   (berhenti saat kuota habis)
```

Cara kerjanya teknis: `domsnap.js` disuntikkan ke setiap halaman TikTok, mengirim snapshot DOM
yang sudah dibersihkan (elemen terlihat + `data-ur`) ke native lewat jembatan `UnrepostDom`.
`DomEngine` menganalisis snapshot dan memutuskan aksi (klik elemen, buka menu, gulir, kembali),
`DomRunner` mengeksekusinya lewat JS **di thread UI**.

Yang diuji otomatis: **99 cek** — termasuk `DomEngine` (20 skenario: grid, menu, konfirmasi,
kuota, halaman login, menu tanpa aksi) dan `domsnap.js` di **HTML asli via jsdom** (13 cek:
snapshot, klik tepat satu kali memicu handler halaman, menu, klik koordinat).

## 🔑 v2.4 — layar login diperjelas

Sebelumnya layar login membingungkan (dua cara berdampingan, tanpa status). Sekarang:

- **Satu tombol utama**: "Buka halaman login TikTok →" (halaman resmi di dalam aplikasi).
- **Daftar status 3 langkah** yang selalu terlihat:
  `1 Login TikTok → 2 Kembali ke Unrepost (otomatis) → 3 Mulai hapus repost`,
  masing-masing berubah **belum → ✓ selesai**.
- **Deteksi otomatis**: begitu Anda kembali dari halaman TikTok (back / Activity resume),
  aplikasi memeriksa cookie sesi dan langsung menampilkan **"Login terdeteksi — siap dipakai"**,
  mengisi nama akun, lalu membuka dashboard sendiri.
- **Tombol cadangan** "Saya sudah login — periksa sekarang" bila deteksi otomatis belum jalan.
- **Form cepat** (email+sandi) dipindah ke dalam lipatan "Cara lain…" — jelas ditandai opsional
  dan tidak bisa untuk akun Google/2FA.

## 📲 Alur pemakaian (3 langkah, tanpa Setelan)

1. Instal APK → buka **Unrepost** (langsung tampil halaman situs, bukan menu Android).
2. **Login**: isi email/username + sandi → *Masuk*. Kalau akun Anda pakai Google atau 2FA,
   tekan **"Buka halaman login TikTok"** (halaman resmi, bisa Google/2FA) lalu
   **"Saya sudah login — periksa sesi"**.
3. Di tab **Bersihkan** (Mode Otomasi): atur batch (1–60) & jeda (default 12 dtk) → tekan
   **🗑 Mulai hapus repost otomatis**. Aplikasi membuka halaman TikTok Anda dan bekerja sendiri;
   ada progres, **Berhenti**, log langsung, dan riwayat.

Kalau macet: tekan **"Periksa halaman & salin diagnostik"** — hasilnya (snapshot + log) bisa
langsung dikirim ke saya untuk menambah label tombol versi TikTok Anda.

> **Rekomendasi**: jalankan **Mode latihan** sekali (tidak menghapus, hanya mencatat) untuk
> memastikan API TikTok versi Anda menerima perintahnya, lalu mulai batch kecil (10–20 video).

## 🔧 v2.2 — memperbaiki "aplikasi keluar sendiri saat login"

Penyebabnya ditemukan di kode (bukan tebak-tebakan):

| Penyebab | Perbaikan |
|---|---|
| Proses Chromium (WebView) TikTok **mati karena kehabisan memori** saat login — mematikan proses aplikasi tanpa `onRenderProcessGone()` | Ditangani: WebView TikTok ditutup dengan pesan + WebView utama dimuat ulang; aplikasi tidak ikut mati |
| `evaluateJavascript()` dipanggil dari **thread latar** (setelah HTTP selesai) — Android bisa langsung mematikan proses | Hasil HTTP sekarang dikirim ke thread UI lewat `Handler`, dibungkus try/catch |
| `android:largeHeap` belum aktif | Aktif (`largeHeap="true"`) agar login berat tidak kehabisan memori |
| Crash tak tertangani = aplikasi hilang tanpa pesan | Dipasang penangkap terakhir: muncul toast "Aplikasi terhenti: …" (tidak lagi hilang diam-diam) |
| WebView login TikTok terlalu berat (zoom, multi-window, autoplay) | Dimatikan semua → WebView ringan |
| **Login lewat form cepat rawan** untuk akun Google/2FA | **Urutan dibalik**: sekarang **Cara 1 = login resmi TikTok** (Google/2FA didukung, paling stabil). Form cepat jadi **Cara 2 (opsional, tersembunyi)** |

Tambahan: kartu **Diagnostik** di tab Pengaturan (info perangkat + tombol **"Salin info + log"**)
supaya kalau masih ada masalah, log-nya bisa langsung dikirim ke pengembang.

**Kalau masih keluar sendiri:** buka Pengaturan → Diagnostik → **Salin info + log**, lalu kirim ke
saya. Log menyebut langkah terakhir yang berhasil, jadi penyebabnya bisa langsung terlihat.

## 🧠 Bagaimana "tanpa Setelan" itu bisa bekerja

Situs di dalam APK memanggil API TikTok langsung dari HP Anda:

| Langkah | Endpoint |
|---|---|
| Login | `POST /api/v1/user/login/` (form: username, password) |
| Ambil daftar repost profil | `GET /api/post/item_list/` |
| Hapus satu repost | `POST /api/v1/aweme/digest/undo/repost/` → fallback `POST /aweme/v1/undigest/` |

Supaya benar-benar jalan di WebView, semua request dikirim lewat **jembatan native**
(`UnrepostNative.api(...)` → `HttpURLConnection` + cookie dari `CookieManager`). Ini penting
karena `fetch` di WebView diblokir CORS oleh TikTok dan POST tidak bisa di-proxy lewat
`shouldInterceptRequest` (body-nya tidak tersedia). Jadi login di WebView TikTok dan proses
hapus memakai **cookie sesi yang sama**.

**Yang harus diketahui (jujur, tanpa menutupi):**

- Endpoint di atas adalah API web internal TikTok — **tidak dijamin** stabil. Kalau TikTok
  mengganti nama/parameter, proses bisa berhenti dengan pesan error; itu terlihat jelas di log.
- **Login Google tidak bisa lewat form cepat** (Google memblokir WebView). Gunakan jalur
  "Buka halaman login TikTok" untuk akun Google/2FA.
- Jeda 10–15 detik itu bukan hiasan: otomasi tanpa jeda wajar berisiko ditandai bot oleh TikTok.
  Aplikasi juga berhenti sendiri setelah 3 kegagalan beruntun.
- Hanya menyentuh tab **Repost** milik akun Anda; tidak pernah feed For You, tidak pernah video
  unggahan.

---

# Bagian lama: penyaring repost (mode aksesibilitas)

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
bash tools/test.sh          # 64 cek, semua lulus
#  DetectorTest      18/18  deteksi repost dari caption
#  CleanerEngineTest 21/21  alur hapus via aksesibilitas (mode lama)
#  web-test          25/25  logika situs: login (salah/benar), jalur website, batch 15 video,
#                           jeda, mode latihan, berhenti, 3x gagal → stop, riwayat, logout
```

Pratinjau tampilan tanpa HP: `docs/mockup.html` (mockup dua layar) atau jalankan
`python3 -m http.server` di root repo lalu buka
`http://localhost:8000/app/assets/web/index.html?mock=app`.

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
    WebAppActivity.java        # layar utama: WebView + jembatan native (HTTP, notifikasi, dsb)
    RepostLog.java             # riwayat (FIFO 300, anti-duplikat)
    Prefs.java                 # setelan & statistik
    HistoryActivity.java       # daftar repost yang ditandai
app/assets/web/                # "website" Unrepost yang dijalankan di dalam APK
  index.html  style.css  app.js
web-test/                      # test logika situs (node, fetch di-mock)
docs/mockup.html               # pratinjau tampilan tanpa HP
tools/
  fetch-toolchain.sh  build-apk.sh  test.sh  lambda-stub/
tools/test/com/unrepost/app/DetectorTest.java
dist/unrepost-debug.apk
```

## 📄 Lisensi

MIT — lihat [LICENSE](LICENSE). Nama, merek, dan logo TikTok adalah milik ByteDance; ikon di
proyek ini hanya ilustrasi gaya bebas untuk aplikasi pihak ketiga.
