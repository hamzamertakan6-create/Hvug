# OrangeX Browser

Basit bir Android WebView tarayıcısı: turuncu/siyah tema, Bing arama (alt bar),
sekmeler, gizli sekme (gözlük ikonu, turuncu tema), favoriler ızgarası, sayfada
bul, basit reklam engelleyici ve Gemini destekli sayfa özetleme / "X AI" soru-cevap.

## GitHub'a yükleyip APK almak (uğraşmadan)

1. Bu klasörün tamamını yeni bir GitHub reposuna yükle (repo kökü bu klasör olacak,
   yani `settings.gradle`, `app/`, `.github/` hepsi kök dizinde).
2. `main` dalına push ettiğinde **Actions** sekmesi otomatik çalışır.
3. İşlem bitince Actions > ilgili run > **Artifacts** kısmından
   `OrangeX-debug-apk` dosyasını indir, içinden `app-debug.apk` çıkar.
4. Telefonunda "bilinmeyen kaynaklardan yükleme" izni açıp APK'yı kur.

Elle de deneyebilirsin: repo kökünde `gradle assembleDebug` (Android Studio
projeyi açtığında Gradle wrapper'ı kendisi indirir, `Build > Build APK` yeterli).

## Neler gerçek, neler basitleştirilmiş

- **Gerçek ve çalışır:** WebView tabanlı gezinme, Bing arama, sekme açma/kapama/geçiş,
  favoriler (uzun basınca kaldırma), sayfada bulma, basit domain-tabanlı reklam
  engelleyici, tema renkleri (özel/gizli sekmede turuncu, normalde siyah).
- **Kendi API anahtarınla çalışır:** "Özetle" ve "X AI" — ücretsiz bir Gemini API
  anahtarı (ai.google.dev) alıp "Özetle" butonuna **uzun basarak** girmen gerekiyor.
  Anahtar sadece cihazında saklanır, koda gömülü değildir.
- **Basitleştirilmiş:** Gizli sekme tam profil izolasyonu yapmaz (Chrome/Brave
  seviyesinde ayrı depolama motoru yok); jest tabanlı panel açma/kapama animasyonsuz,
  anlık geçişlerle çalışır; reklam engelleyici küçük bir domain listesiyle sınırlı;
  "X AI ekranı görsün" kısmı görsel ekran görüntüsü değil, sayfanın metnini AI'ya
  gönderiyor.
- **Yok:** Proxy/VPN, gerçek Chromium motoru, çoklu profil, gerçek zamanlı ekran
  görüntüsü analizi. Bunlar Brave seviyesinde ayrı büyük altyapı projeleri.

## Paket adı

`com.orangex.browser` — istersen `app/build.gradle` içindeki `applicationId` ve
`namespace` alanlarını değiştirebilirsin.
