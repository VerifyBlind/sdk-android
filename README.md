# VerifyBlind Android SDK

> **Kimliğinizi Kanıtlayın, Gizliliğinizi Koruyun** · _Prove Your Identity, Protect Your Privacy_

**[🇹🇷 Türkçe](#türkçe) · [🇬🇧 English](#english)**

---

## Türkçe

Android uygulamalarına VerifyBlind kimlik doğrulaması entegre etmek için resmi Android SDK.

## Mimari

```
[Partner Android App (SDK)]      ← Geçici RSA-OAEP anahtar çifti burada üretilir
        │
        │  POST { public_key, validations, custom_data }
        ▼
[Partner'ın Backend Sunucusu]    ← Aracı: X-API-Key ekler, validations'ı KENDİ ayarından koyar
        │
        │  POST /api/pop/generate
        ▼
[VerifyBlind API]  ──► { nonce }
        │
        ▼
[Partner Android App (SDK)]
        │
        │  https://app.verifyblind.com/request?nonce=...&pk_hash=...
        ▼
[VerifyBlind Mobil Uygulaması]   ← Kullanıcı kimliğini doğrular
        │
        ▼
[Partner Android App (SDK)]      ← GET /api/pop/result/{nonce} ile sonucu sorar,
                                    şifreli yanıtı geçici özel anahtarla cihazda çözer
```

API anahtarınız yalnızca backend'inizde durur; mobil uygulamada hiçbir gizli anahtar bulunmaz.

## Kurulum

SDK, Maven Central'da yayımlanır. `mavenCentral()` deposu (Android projelerinde varsayılan olarak
açıktır) yeterlidir:

```kotlin
// app/build.gradle.kts
dependencies {
    implementation("com.verifyblind:verifyblind-android:1.0.1")
}
```

Gereksinimler: `minSdk 24`, Java 17.

## Hızlı Başlangıç

```kotlin
import com.verifyblind.sdk.VerifyBlindAndroidSDK
import com.verifyblind.sdk.VerifyBlindConfig
import com.verifyblind.sdk.VerifyBlindException

val sdk = VerifyBlindAndroidSDK(
    VerifyBlindConfig(
        partnerBackendUrl      = "https://partner.example.com/api/auth/",
        generateEndpoint       = "verifyblind-generate",
        verifyblindAppLinkBase = "https://app.verifyblind.com/request"
    )
)

viewModelScope.launch {
    try {
        val start = sdk.startAuthentication(
            context     = context,
            validations = mapOf("age" to "18+", "user_id" to true)  // İsteğe bağlı
        )
        // VerifyBlind uygulaması açılır. Kullanıcı uygulamanıza döndüğünde sonucu sorun:
        val result = sdk.checkVerificationResult(start.nonce)  // null = henüz bekliyor
        // result["token"] → imzalı ham yanıt; KARAR İÇİN bunu sunucunuza gönderin (aşağıya bakın)
        // result["validations"] → { age: true, user_id: "...", nsbd_id: "...", doc_id: "..." }
    } catch (e: VerifyBlindException) {
        // e.code: NETWORK_ERROR | PARTNER_BACKEND_ERROR | INVALID_RESPONSE
        //         APP_LINK_FAILED | USER_CANCELLED (e.cancelReason ile)
    }
}
```

**Sunucuda doğrulama (önemli):** Engelleme, ödül ya da yaş kapısı gibi bir kararı telefondaki sonuca göre
vermeyin; uygulama kandırılabilir. `result["token"]`'ı kendi sunucunuza gönderin. Sunucunuz web
entegrasyonundakiyle **aynı doğrulamayı** yapar: imzayı (RSA-PSS, enclave anahtarı) doğrular, nonce'u
tek seferlik tüketir ve sonucu nonce ile sakladığı koşula göre okur. Ayrıntılar:
https://verifyblind.com/ai-integration.md (desen A, 4. adım).

`checkVerificationResult`, sonuç hazır değilken `null` döner; birkaç saniye arayla tekrar çağırın. Kullanıcı
işlemi iptal ettiyse `USER_CANCELLED` koduyla `VerifyBlindException` fırlatır; iptal nedeni
`cancelReason` alanındadır (`no_card_registered`, `user_declined`, `fingerprint_failed`, `session_expired`,
`user_cancelled`). Nonce 15 dakika geçerlidir.

### Doğrulamadan sonra uygulamanıza dönüş (app-to-app)

VerifyBlind'ı kendi mobil uygulamanızdan açtığınızda `returnUrl` verirseniz, akış bittiğinde (başarı ya da
iptal) VerifyBlind kullanıcıyı **uygulamanıza geri getirir**.

```kotlin
val start = sdk.startAuthentication(
    context     = context,
    validations = mapOf("user_id" to true),
    returnUrl   = "verifyblinddemo://callback"   // uygulamanızın özel şeması
)
```

İşlem bitince VerifyBlind `verifyblinddemo://callback?nonce={nonce}&status=success` (ya da
`status=cancelled`) adresini açar ve uygulamanız öne gelir. Sonucu okumak için `checkVerificationResult`
ile sormaya devam edin.

**Gereken iki adım:**

1. Şemayı VerifyBlind Partner Portalı → *Ayarlar → Uygulamaya Dönüş Şeması* bölümüne kaydedin
   (ör. `verifyblinddemo`). VerifyBlind yalnızca **kayıtlı şemayla eşleşen** dönüş adreslerini açar;
   alan boşsa uygulamaya dönüş kapalıdır.
2. Şemayı `AndroidManifest.xml` içinde tanımlayın:

```xml
<activity android:name=".MainActivity" android:exported="true">
    <intent-filter>
        <action android:name="android.intent.action.VIEW" />
        <category android:name="android.intent.category.DEFAULT" />
        <category android:name="android.intent.category.BROWSABLE" />
        <data android:scheme="verifyblinddemo" android:host="callback" />
    </intent-filter>
</activity>
```

> QR (cihazlar arası) akışlarında `returnUrl` kullanılmaz — dönülecek bir uygulama yoktur.

## Partner Backend Endpoint'i

Partner backend'inizde şu endpoint'i oluşturun:

```typescript
// Node.js örneği — kimlik doğrulama X-API-Key header'ı ile.
// SDK'nın gönderdiği gövde: { public_key, validations?, custom_data? }
// Ne sorulacağına SUNUCUNUZ karar verir: uygulamanın gönderdiği validations kullanılmaz.
// (İstek değiştirilebilir — "18+" yerine "1+" soran biri de imzalı age: true alır.)
const VALIDATIONS = { age: '18+', user_id: true };

app.post('/api/auth/verifyblind-generate', async (req, res) => {
    const { public_key, custom_data } = req.body;
    const response = await fetch('https://api.verifyblind.com/api/pop/generate', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', 'X-API-Key': process.env.VERIFYBLIND_API_KEY },
        body: JSON.stringify({ public_key, custom_data, validations: VALIDATIONS }),
    });
    // Durum kodunu koru; relay { nonce } döner. pk_hash'i SDK kendisi hesaplar.
    // Sonucu sunucuda kontrol edecekseniz nonce'u sorduğunuz koşulla birlikte saklayın (TTL 960 sn).
    res.status(response.status).json(await response.json());
});
```

> **Not:** SDK, `additional_data` yerine `custom_data` alanını gönderir; relay `/api/pop/generate`
> ikisini de kabul eder. `public_key` ve `custom_data` aynen iletilir; `validations` ise her zaman
> sunucunuzun ayarından gelir. İmzalı sonuçtaki `validations.age_condition` (yeni enclave sürümlerinde)
> sorulan koşulu gösterir; sizin sorduğunuz koşula eşit olmalıdır.

## Tekillik / Tanıma Kodları

`validations` içinde `user_id: true` isterseniz, çözülen yanıtta **üç kod birden** döner — üçünü de saklayın:

| Alan | Anlam |
|------|-------|
| `user_id` | Ulusal-no bazlı kimlik. Partner'a özel HMAC. Türetilemezse alan yanıtta yer almaz. |
| `nsbd_id` | Biyografik kişi kodu; **ad ve doğum tarihi değişmediği sürece** kişinin kartları arasında sabit. **Olasılıksal ipucu** — tek başına sert dedup kararı vermeyin. Kayabileceği durumlar: isim değişikliği (ör. evlilik) ve uzun isimlerin farklı belgelerde farklı kırpılması (MRZ kırpması ICAO 9303'te ihraççı takdirindedir). Sert karar için `doc_id` kullanın. |
| `doc_id` | Belge kodu; aynı `doc_id` = aynı fiziksel belge = aynı kişi (sert sinyal). |

Üçü de partner'a özeldir (başka partner ile eşleştirilemez) ve TCKN'ye döndürülemez. Demo kartla yapılan
doğrulamalarda `validations.is_test: true` döner ve kodlar `TEST_` önekiyle gelir.

## Konfigürasyon Parametreleri

| Parametre | Zorunlu | Varsayılan | Açıklama |
|-----------|---------|-----------|----------|
| `partnerBackendUrl` | ✅ | — | Backend'inizdeki aracı (proxy) endpoint'in taban adresi |
| `generateEndpoint` | ❌ | `.` | `partnerBackendUrl`'e eklenen göreli yol |
| `verifyblindAppLinkBase` | ✅ | — | VerifyBlind App Link adresi: `https://app.verifyblind.com/request` |
| `verifyblindApiUrl` | ❌ | `https://api.verifyblind.com` | Sonucun sorgulandığı VerifyBlind API adresi |
| `certificatePins` | ❌ | `null` | İsteğe bağlı sertifika sabitleme listesi (OkHttp pin biçimi) |
| `skipSecurityChecks` | ❌ | `false` | Yalnızca geliştirme ortamı içindir; sertifika sabitlemeyi kapatır |

## Güvenlik Notları

- **API anahtarı asla mobil uygulamada olmamalıdır.** Anahtar yalnızca backend'inizdeki aracı endpoint'te kullanılır.
- Sonuç, cihazda üretilen geçici anahtarla şifreli gelir ve yalnızca aynı `VerifyBlindAndroidSDK` örneği çözebilir.
- SDK, çözülmüş sonucu uygulamanıza verir; enclave imzasını ve kanıtını ayrıca döndürmez. Sonucun
  sunucunuzda imzasıyla doğrulanması gereken senaryolarda web entegrasyonunu kullanın
  (bkz. [Geliştirici Dokümantasyonu](https://verifyblind.com/developers)).
- `skipSecurityChecks=true` yalnızca geliştirme/test ortamı içindir. Üretimde kullanmayınız.

## Sürüm Geçmişi

| Sürüm | Açıklama |
|-------|----------|
| 1.0.1 | Sonuçta `token` alanı: enclave'in imzaladığı ham yanıt (web widget token'ıyla aynı biçim); sunucuda doğrulamak için |
| 1.0.0 | İlk sürüm: geçici anahtarlı (PoP) akış, validations, App Link ve uygulamaya dönüş desteği |

---

## English

The official Android SDK for integrating VerifyBlind identity verification into Android apps.

### Architecture

```
[Partner Android App (SDK)]      ← A temporary RSA-OAEP key pair is generated here
        │
        │  POST { public_key, validations, custom_data }
        ▼
[Partner Backend Server]         ← Proxy: adds X-API-Key, sets validations from ITS OWN config
        │
        │  POST /api/pop/generate
        ▼
[VerifyBlind API]  ──► { nonce }
        │
        ▼
[Partner Android App (SDK)]
        │
        │  https://app.verifyblind.com/request?nonce=...&pk_hash=...
        ▼
[VerifyBlind Mobile App]         ← The user verifies their identity
        │
        ▼
[Partner Android App (SDK)]      ← Polls GET /api/pop/result/{nonce} and decrypts the
                                    encrypted response on the device with the temporary key
```

Your API key stays only on your backend; there is no secret in the mobile app.

### Installation

The SDK is published on Maven Central. The `mavenCentral()` repository (enabled by default in Android
projects) is all you need:

```kotlin
// app/build.gradle.kts
dependencies {
    implementation("com.verifyblind:verifyblind-android:1.0.1")
}
```

Requirements: `minSdk 24`, Java 17.

### Quick Start

```kotlin
import com.verifyblind.sdk.VerifyBlindAndroidSDK
import com.verifyblind.sdk.VerifyBlindConfig
import com.verifyblind.sdk.VerifyBlindException

val sdk = VerifyBlindAndroidSDK(
    VerifyBlindConfig(
        partnerBackendUrl      = "https://partner.example.com/api/auth/",
        generateEndpoint       = "verifyblind-generate",
        verifyblindAppLinkBase = "https://app.verifyblind.com/request"
    )
)

viewModelScope.launch {
    try {
        val start = sdk.startAuthentication(
            context     = context,
            validations = mapOf("age" to "18+", "user_id" to true)  // Optional
        )
        // The VerifyBlind app opens. When the user returns to your app, ask for the result:
        val result = sdk.checkVerificationResult(start.nonce)  // null = still pending
        // result["token"] → the signed raw answer; send THIS to your server for any decision (see below)
        // result["validations"] → { age: true, user_id: "...", nsbd_id: "...", doc_id: "..." }
    } catch (e: VerifyBlindException) {
        // e.code: NETWORK_ERROR | PARTNER_BACKEND_ERROR | INVALID_RESPONSE
        //         APP_LINK_FAILED | USER_CANCELLED (with e.cancelReason)
    }
}
```

**Verify on your server (important):** do not make a decision (blocking, rewards, age gates) from the
result on the phone; an app can be tampered with. Send `result["token"]` to your own server. It runs the
**same check** as a web integration: verify the signature (RSA-PSS, enclave key), consume the nonce once,
and read the result against the condition stored with the nonce. Details:
https://verifyblind.com/ai-integration.md (pattern A, step 4).

`checkVerificationResult` returns `null` while the result is not ready; call it again every few seconds.
If the user cancelled, it throws a `VerifyBlindException` with code `USER_CANCELLED`; the reason is in
`cancelReason` (`no_card_registered`, `user_declined`, `fingerprint_failed`, `session_expired`,
`user_cancelled`). The nonce is valid for 15 minutes.

### Returning to your app after verification (app-to-app)

When you launch VerifyBlind from your own mobile app, pass a `returnUrl` so VerifyBlind brings the user
**back to your app** when the flow ends (success or cancel) — instead of leaving them inside VerifyBlind.

```kotlin
val start = sdk.startAuthentication(
    context     = context,
    validations = mapOf("user_id" to true),
    returnUrl   = "verifyblinddemo://callback"   // your app's custom scheme
)
```

When done, VerifyBlind opens `verifyblinddemo://callback?nonce={nonce}&status=success` (or
`status=cancelled`), which foregrounds your app. Keep calling `checkVerificationResult` to read the result.

**Two required steps:**

1. **Register the scheme** in the VerifyBlind Partner Portal → *Settings → App Return Scheme*
   (e.g. `verifyblinddemo`). VerifyBlind only opens a return URL whose **scheme matches your registered
   value** (fail-closed — prevents open-redirect). Leaving it empty disables app return.
2. **Declare the scheme** in your `AndroidManifest.xml` so the callback opens your app:

```xml
<activity android:name=".MainActivity" android:exported="true">
    <intent-filter>
        <action android:name="android.intent.action.VIEW" />
        <category android:name="android.intent.category.DEFAULT" />
        <category android:name="android.intent.category.BROWSABLE" />
        <data android:scheme="verifyblinddemo" android:host="callback" />
    </intent-filter>
</activity>
```

> QR (cross-device) flows ignore `returnUrl` — there is no caller app to return to.

### Partner Backend Endpoint

Create this endpoint on your partner backend:

```typescript
// Node.js example — authentication is the X-API-Key header.
// Body sent by the SDK: { public_key, validations?, custom_data? }
// YOUR SERVER decides what is asked: the validations sent by the app are not used.
// (The request can be edited — someone who asks "1+" instead of "18+" also gets a signed age: true.)
const VALIDATIONS = { age: '18+', user_id: true };

app.post('/api/auth/verifyblind-generate', async (req, res) => {
    const { public_key, custom_data } = req.body;
    const response = await fetch('https://api.verifyblind.com/api/pop/generate', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', 'X-API-Key': process.env.VERIFYBLIND_API_KEY },
        body: JSON.stringify({ public_key, custom_data, validations: VALIDATIONS }),
    });
    // Preserve the status code; the relay returns { nonce }. The SDK computes pk_hash itself.
    // If your server checks the result later, store the nonce with the condition you asked (TTL 960 s).
    res.status(response.status).json(await response.json());
});
```

> **Note:** the SDK sends `custom_data` instead of `additional_data`; the relay's `/api/pop/generate`
> accepts both. Forward `public_key` and `custom_data` unchanged; `validations` always come from your
> server configuration. In the signed result, `validations.age_condition` (newer enclave releases)
> states the condition that was asked; it must equal the condition you asked.

### Uniqueness / Recognition Codes

If you request `user_id: true` in `validations`, the decrypted response returns **three codes at once** —
store all three:

| Field | Meaning |
|-------|---------|
| `user_id` | National-ID-based identity. Partner-specific HMAC. Left out of the response if it cannot be derived. |
| `nsbd_id` | Biographic person code; stable across a person's cards **as long as name and date of birth do not change**. A **probabilistic hint** — do not make a hard dedup decision on it alone. It can drift on a name change (e.g. marriage) or when a long name is truncated differently on different documents (MRZ truncation is at the issuer's discretion under ICAO 9303). Use `doc_id` for hard decisions. |
| `doc_id` | Document code; same `doc_id` = same physical document = same person (hard signal). |

All three are partner-specific (cannot be linked across partners) and cannot be reversed to the national ID
number. Verifications made with a demo card return `validations.is_test: true`, and the codes carry a
`TEST_` prefix.

### Configuration Parameters

| Parameter | Required | Default | Description |
|-----------|----------|---------|-------------|
| `partnerBackendUrl` | ✅ | — | Base URL of the proxy endpoint on your backend |
| `generateEndpoint` | ❌ | `.` | Relative path appended to `partnerBackendUrl` |
| `verifyblindAppLinkBase` | ✅ | — | VerifyBlind App Link: `https://app.verifyblind.com/request` |
| `verifyblindApiUrl` | ❌ | `https://api.verifyblind.com` | VerifyBlind API the result is polled from |
| `certificatePins` | ❌ | `null` | Optional certificate pin list (OkHttp pin format) |
| `skipSecurityChecks` | ❌ | `false` | For development only; disables certificate pinning |

### Security Notes

- **The API key must never be in the mobile app.** It is used only in the proxy endpoint on your backend.
- The result arrives encrypted with the temporary key generated on the device, and only the same
  `VerifyBlindAndroidSDK` instance can decrypt it.
- The SDK gives your app the decrypted result; it does not also return the enclave signature and proof.
  Where your server must verify the result by its signature, use the web integration
  (see the [Developer Documentation](https://verifyblind.com/developers)).
- `skipSecurityChecks=true` is for development/testing only. Do not use it in production.

### Version History

| Version | Description |
|---------|-------------|
| 1.0.1 | `token` in the result: the enclave-signed raw answer (same format as the web widget token), for server-side verification |
| 1.0.0 | First release: temporary-key (PoP) flow, validations, App Link and app-return support |

---

## Lisans · License

Apache License 2.0 — bkz. / see [LICENSE](LICENSE).
