# Tidak ada library pihak ketiga. Aturan ini hanya menjaga kelas inti
# (dipakai lewat refleksi oleh Android) agar tidak dibuang saat minify.
-keep class com.unrepost.app.UnrepostService { *; }
-keep class com.unrepost.app.MainActivity { *; }
-keep class com.unrepost.app.HistoryActivity { *; }
-dontwarn org.conscrypt.**
