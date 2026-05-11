# VerifyBlind Android SDK

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
