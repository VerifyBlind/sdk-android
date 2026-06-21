# VerifyBlind Android SDK

**[🇹🇷 Türkçe](#türkçe) · [🇬🇧 English](#english)**

---

## Türkçe

Android uygulamalarına VerifyBlind kimlik doğrulaması entegre etmek için resmi Android SDK.

## Mimari 

```
[Partner Android App (SDK)]
        │
        │  POST { validations, custom_data }
        ▼
[Partner'ın Backend Sunucusu]   ← Private Key BURADA, asla mobil uygulamada değil!
        │
        │  İmzalı request (Node/NET SDK ile)
        ▼
[VerifyBlind API]
        │
        │  { nonce, pkHash }
        ▼
[Partner Android App (SDK)]
        │
        │  https://app.verifyblind.com/request?nonce=...&pk_hash=...
        ▼
[VerifyBlind Mobile]               ← Kullanıcı kimlik doğrular
        │
        │  Callback (partner backend'e)
        ▼
[Partner Backend]               ← Şifreli yanıtı .NET/Node SDK ile çözer
```

## Kurulum

### `build.gradle.kts`

```kotlin
// settings.gradle.kts'e SDK modülünü ekleyin:
include(":verifyblind")
project(":verifyblind").projectDir = File("path/to/verifyblind-android/verifyblind")

// app/build.gradle.kts:
dependencies {
    implementation(project(":verifyblind"))
}
```

## Hızlı Başlangıç

```kotlin
import com.verifyblind.sdk.VerifyBlindAndroidSDK
import com.verifyblind.sdk.VerifyBlindConfig
import com.verifyblind.sdk.VerifyBlindException

val sdk = VerifyBlindAndroidSDK(
    VerifyBlindConfig(
        partnerBackendUrl = "https://partner.example.com/api/auth/verifyblind-generate"
    )
)

viewModelScope.launch {
    try {
        val result = sdk.startAuthentication(
            context     = context,
            validations = mapOf("age_min" to 18)  // İsteğe bağlı
        )
        // VerifyBlind uygulaması otomatik açılır
        // result.nonce, result.pkHash bilgi için döner
    } catch (e: VerifyBlindException) {
        // e.code: NETWORK_ERROR | PARTNER_BACKEND_ERROR
        //         INVALID_RESPONSE | APP_LINK_FAILED
    }
}
```

## Partner Backend Endpoint'i

Partner backend'inizde şu endpoint'i oluşturun:

```typescript
// Node.js örneği
app.post('/api/auth/verifyblind-generate', async (req, res) => {
    const { validations, custom_data } = req.body;

    // .NET/Node SDK ile imzalı request oluştur
    const signedRequest = VerifyBlindClient.createRequest({
        customData: { ...validations, ...custom_data }
    });

    // VerifyBlind API'ye gönder
    await axios.post('https://api.verifyblind.com/api/PartnerRequest/generate', {
        request: signedRequest.request,
        sign: signedRequest.sign
    });

    // SDK'ya nonce + pkHash döndür
    res.json({ nonce: signedRequest.request.nonce, pkHash: signedRequest.pkHash });
});
```

## Tekillik / Tanıma Kodları

`validations` içinde `user_id: true` isterseniz, çözülen yanıtta **üç kod birden** döner — üçünü de saklayın:

| Alan | Anlam |
|------|-------|
| `user_id` | Ulusal-no bazlı kimlik (TCKN yoksa boş). Partner'a özel HMAC. |
| `nsbd_id` | Biyografik kişi kodu; kişinin tüm kartlarında sabit. **Olasılıksal ipucu** — tek başına sert dedup kararı vermeyin. |
| `doc_id` | Belge kodu; aynı `doc_id` = aynı fiziksel belge = aynı kişi (sert sinyal). |

Üçü de partner'a özeldir (başka partner ile eşleştirilemez) ve TCKN'ye döndürülemez. Üçünü birlikte saklamak, bir ülke ulusal kimlik numarasını sonradan kaldırsa/eklese veya kullanıcı kartını yenilese bile aynı kişiyi tanımanızı sağlar.

## Konfigürasyon Parametreleri

| Parametre | Zorunlu | Varsayılan | Açıklama |
|-----------|---------|-----------|----------|
| `partnerBackendUrl` | ✅ | — | Signed request üretip nonce döndüren endpoint |
| `verifyblindAppLinkBase` | ❌ | `https://app.verifyblind.com/request` | VerifyBlind App Link base URL |
| `skipSecurityChecks` | ❌ | `false` | Yalnızca geliştirme ortamı içindir |

## Güvenlik Notları

- **Private Key asla mobil uygulamada olmamalıdır.** İmzalama işlemi partner'ın backend sunucusunda gerçekleşir.
- `skipSecurityChecks=true` yalnızca geliştirme/test ortamı içindir. Üretimde kullanmayınız.
## Sürüm Geçmişi

| Sürüm | Açıklama |
|-------|----------|
| 1.0.0 | İlk sürüm: validations, App Link desteği |

---

## English

The official Android SDK for integrating VerifyBlind identity verification into Android apps.

### Architecture

```
[Partner Android App (SDK)]
        │
        │  POST { validations, custom_data }
        ▼
[Partner's Backend Server]      ← Private Key lives HERE, never in the mobile app!
        │
        │  Signed request (via the Node/.NET SDK)
        ▼
[VerifyBlind API]
        │
        │  { nonce, pkHash }
        ▼
[Partner Android App (SDK)]
        │
        │  https://app.verifyblind.com/request?nonce=...&pk_hash=...
        ▼
[VerifyBlind Mobile]            ← User verifies their identity
        │
        │  Callback (to the partner backend)
        ▼
[Partner Backend]               ← Decrypts the encrypted response via the .NET/Node SDK
```

### Installation

`build.gradle.kts`:

```kotlin
// In settings.gradle.kts, add the SDK module:
include(":verifyblind")
project(":verifyblind").projectDir = File("path/to/verifyblind-android/verifyblind")

// app/build.gradle.kts:
dependencies {
    implementation(project(":verifyblind"))
}
```

### Quick Start

```kotlin
import com.verifyblind.sdk.VerifyBlindAndroidSDK
import com.verifyblind.sdk.VerifyBlindConfig
import com.verifyblind.sdk.VerifyBlindException

val sdk = VerifyBlindAndroidSDK(
    VerifyBlindConfig(
        partnerBackendUrl = "https://partner.example.com/api/auth/verifyblind-generate"
    )
)

viewModelScope.launch {
    try {
        val result = sdk.startAuthentication(
            context     = context,
            validations = mapOf("age_min" to 18)  // Optional
        )
        // The VerifyBlind app opens automatically
        // result.nonce, result.pkHash are returned for reference
    } catch (e: VerifyBlindException) {
        // e.code: NETWORK_ERROR | PARTNER_BACKEND_ERROR
        //         INVALID_RESPONSE | APP_LINK_FAILED
    }
}
```

### Partner Backend Endpoint

Create this endpoint on your partner backend:

```typescript
// Node.js example
app.post('/api/auth/verifyblind-generate', async (req, res) => {
    const { validations, custom_data } = req.body;

    // Build a signed request with the .NET/Node SDK
    const signedRequest = VerifyBlindClient.createRequest({
        customData: { ...validations, ...custom_data }
    });

    // Send it to the VerifyBlind API
    await axios.post('https://api.verifyblind.com/api/PartnerRequest/generate', {
        request: signedRequest.request,
        sign: signedRequest.sign
    });

    // Return nonce + pkHash to the SDK
    res.json({ nonce: signedRequest.request.nonce, pkHash: signedRequest.pkHash });
});
```

### Uniqueness / Recognition Codes

If you request `user_id: true` inside `validations`, the decrypted response returns **three codes at
once** — store all three:

| Field | Meaning |
|-------|---------|
| `user_id` | National-number-based identity (empty if there is no national number). Partner-specific HMAC. |
| `nsbd_id` | Biographic person code; stable across all of a person's cards. **Probabilistic hint** — don't make a hard dedup decision on it alone. |
| `doc_id` | Document code; the same `doc_id` = the same physical document = the same person (hard signal). |

All three are partner-specific (cannot be correlated with another partner) and cannot be reversed to a
national ID number. Storing all three lets you recognize the same person even if a country later removes
or adds a national ID number, or the user renews their card.

### Configuration Parameters

| Parameter | Required | Default | Description |
|-----------|----------|---------|-------------|
| `partnerBackendUrl` | ✅ | — | Endpoint that produces a signed request and returns the nonce |
| `verifyblindAppLinkBase` | ❌ | `https://app.verifyblind.com/request` | VerifyBlind App Link base URL |
| `skipSecurityChecks` | ❌ | `false` | Development environment only |

### Security Notes

- **The private key must never be in the mobile app.** Signing happens on the partner's backend server.
- `skipSecurityChecks=true` is for development/test only. Do not use it in production.

### Version History

| Version | Description |
|---------|-------------|
| 1.0.0 | First release: validations, App Link support |
