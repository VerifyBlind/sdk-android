package com.verifyblind.sdk

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import com.verifyblind.sdk.VerifyBlindException.ErrorCode
import com.verifyblind.sdk.model.StartAuthRequest
import com.verifyblind.sdk.model.StartAuthResult
import com.verifyblind.sdk.network.NetworkClient
import com.verifyblind.sdk.crypto.CryptoUtils
import org.json.JSONObject
import java.security.KeyPair

/**
 * VerifyBlind Android SDK — PoP Mode (Proof of Personhood)
 *
 * ## Akış
 * 1. `startAuthentication()` → Cihazda ephemeral RSA-OAEP keypair üretir
 * 2. Partner backend proxy'ye `{ public_key, validations }` gönderir → `{ nonce }` alır
 * 3. pk_hash = SHA256(publicKeyBase64) hesaplar
 * 4. VerifyBlind App Link açılır: `https://app.verifyblind.com/request?nonce=...&pk_hash=...`
 * 5. `checkVerificationResult()` → VerifyBlind relay'i poll eder → şifreli yanıtı lokal çözer
 *
 * ## Kullanım
 * ```kotlin
 * val sdk = VerifyBlindAndroidSDK(
 *     VerifyBlindConfig(partnerBackendUrl = "https://partner.example.com/api/auth/generate")
 * )
 *
 * viewModelScope.launch {
 *     val result = sdk.startAuthentication(context)
 *     // result.nonce, result.pk_hash
 *
 *     // Deep link açıldı, kullanıcı VerifyBlind app'ı onayladıktan sonra:
 *     val data = sdk.checkVerificationResult(result.nonce)
 * }
 * ```
 */
class VerifyBlindAndroidSDK(private val config: VerifyBlindConfig) {

    private val TAG = "VerifyBlind_SDK"

    private val partnerService = NetworkClient.createPartnerService(config)
    private val relayService = NetworkClient.createRelayService(config.verifyblindApiUrl)

    // Ephemeral keypair — startAuthentication'dan checkVerificationResult'a aktarılır
    private var currentKeyPair: KeyPair? = null

    /**
     * VerifyBlind kimlik doğrulama akışını başlatır (PoP Mode).
     *
     * @param context Android Context (App Link açmak için gerekli).
     * @param validations İsteğe bağlı doğrulama parametreleri (yaş, uyruk vb.).
     * @param customData İsteğe bağlı ek veriler.
     * @return [StartAuthResult] — nonce ve pk_hash.
     * @throws VerifyBlindException Hata durumunda.
     */
    suspend fun startAuthentication(
        context: Context,
        validations: Map<String, Any>? = null,
        customData: Map<String, Any>? = null
    ): StartAuthResult {

        // 1. Ephemeral RSA-OAEP keypair üret
        Log.d(TAG, "[VerifyBlind SDK] Ephemeral RSA keypair üretiliyor...")
        val keyPair = CryptoUtils.generateRsaKeyPair()
        currentKeyPair = keyPair

        // 2. Public key'i Base64 SPKI formatına çevir + pk_hash hesapla
        val publicKeyBase64 = CryptoUtils.exportPublicKeyBase64(keyPair)
        val pkHash = CryptoUtils.computePkHash(publicKeyBase64)
        Log.d(TAG, "[VerifyBlind SDK] pk_hash: ${pkHash.take(16)}...")

        // 3. Partner backend proxy'ye gönder → { nonce } al
        val request = StartAuthRequest(
            public_key = publicKeyBase64,
            validations = validations,
            custom_data = customData
        )

        val response = try {
            partnerService.startAuth(config.generateEndpoint, request)
        } catch (e: Exception) {
            Log.e(TAG, "[VerifyBlind SDK] Partner backend iletişim hatası: ${e.message}", e)
            throw VerifyBlindException(
                "Partner backend'e ulaşılamadı: ${e.message}",
                cause = e,
                code = ErrorCode.NETWORK_ERROR
            )
        }

        if (!response.isSuccessful) {
            val errBody = response.errorBody()?.string() ?: "Bilinmeyen hata"
            Log.e(TAG, "[VerifyBlind SDK] Partner backend hatası ${response.code()}: $errBody")
            throw VerifyBlindException(
                "Partner backend hatası (${response.code()}): $errBody",
                code = ErrorCode.PARTNER_BACKEND_ERROR
            )
        }

        val body = response.body()
        val nonce = body?.nonce

        if (nonce.isNullOrBlank()) {
            throw VerifyBlindException(
                "Partner backend yanıtında 'nonce' bulunamadı. " +
                "Proxy'nin { nonce } döndürdüğünden emin olun.",
                code = ErrorCode.INVALID_RESPONSE
            )
        }

        Log.d(TAG, "[VerifyBlind SDK] Nonce alındı: $nonce")

        // 4. VerifyBlind App Link'ini aç
        openAppLink(context, nonce, pkHash)

        return StartAuthResult(
            nonce = nonce,
            pk_hash = pkHash,
            validations = validations
        )
    }

    /**
     * VerifyBlind relay'i poll ederek doğrulama sonucunu sorgular.
     * Tamamlanmışsa şifreli yanıtı lokal olarak çözer ve sonucu döndürür.
     *
     * @param nonce Sorgulanacak işlemin nonce değeri.
     * @return Map<String, Any>? — Tamamlanmışsa çözülmüş veri, henüz beklemedeyse null.
     */
    suspend fun checkVerificationResult(nonce: String): Map<String, Any>? {
        val keyPair = currentKeyPair ?: run {
            Log.e(TAG, "[VerifyBlind SDK] Keypair bulunamadı — startAuthentication çağrılmamış olabilir.")
            return null
        }

        return try {
            Log.d(TAG, "[VerifyBlind SDK] Relay poll: $nonce")
            val response = relayService.getPopResult(nonce)

            if (!response.isSuccessful) {
                Log.w(TAG, "[VerifyBlind SDK] Poll başarısız: ${response.code()}")
                return null
            }

            val body = response.body() ?: return null
            val status = body.status

            if (status == "cancelled") {
                val reason = body.reason ?: "user_cancelled"
                val msg = when (reason) {
                    "no_card_registered" -> "Kullanıcının VerifyBlind uygulamasında kayıtlı kimlik kartı yok. Önce uygulamaya kimliğini eklemesi gerekiyor."
                    "user_declined"      -> "Kullanıcı doğrulama isteğini reddetti."
                    "fingerprint_failed" -> "Parmak izi / biyometrik doğrulama başarısız oldu."
                    "session_expired"    -> "Mobil oturum süresi doldu. Yeni QR ile tekrar deneyin."
                    else                 -> "Kullanıcı kimlik doğrulamayı iptal etti."
                }
                Log.w(TAG, "[VerifyBlind SDK] İptal: reason=$reason — $msg")
                throw VerifyBlindException(
                    msg,
                    code = VerifyBlindException.ErrorCode.USER_CANCELLED,
                    cancelReason = reason
                )
            }

            if (status != "completed" && status != "SUCCESS") {
                Log.d(TAG, "[VerifyBlind SDK] Henüz tamamlanmadı. Status: $status")
                return null
            }

            val encryptedResponse = body.encrypted_response ?: run {
                Log.w(TAG, "[VerifyBlind SDK] completed ama encrypted_response yok.")
                return null
            }

            // Lokal decrypt
            Log.d(TAG, "[VerifyBlind SDK] Şifreli yanıt çözülüyor...")
            val plaintext = CryptoUtils.decryptHybridResponse(
                encKey = encryptedResponse.enc_key,
                blob = encryptedResponse.blob,
                keyPair = keyPair
            )

            // { payload: "...", signature: "..." } → payload'ı parse et
            val json = JSONObject(plaintext)
            val payloadStr = json.optString("payload")
                .takeIf { it.isNotEmpty() } ?: plaintext

            val payloadJson = JSONObject(payloadStr)
            val result = mutableMapOf<String, Any>()
            payloadJson.keys().forEach { key ->
                when (val value = payloadJson.get(key)) {
                    is Boolean -> result[key] = value
                    is Int -> result[key] = value
                    is Long -> result[key] = value
                    is Double -> result[key] = value
                    is String -> result[key] = value
                    is org.json.JSONObject -> result[key] = parseJsonObject(value)
                    else -> result[key] = value.toString()
                }
            }

            Log.i(TAG, "[VerifyBlind SDK] Doğrulama tamamlandı.")
            result

        } catch (e: VerifyBlindException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "[VerifyBlind SDK] checkVerificationResult hatası: ${e.message}", e)
            null
        }
    }

    private fun parseJsonObject(json: JSONObject): Map<String, Any> {
        val map = mutableMapOf<String, Any>()
        json.keys().forEach { key ->
            when (val value = json.get(key)) {
                is Boolean -> map[key] = value
                is Int -> map[key] = value
                is Long -> map[key] = value
                is Double -> map[key] = value
                is String -> map[key] = value
                is JSONObject -> map[key] = parseJsonObject(value)
                else -> map[key] = value.toString()
            }
        }
        return map
    }

    /**
     * VerifyBlind Mobile uygulamasını App Link ile açar.
     * URL: https://app.verifyblind.com/request?nonce={nonce}&pk_hash={pkHash}
     */
    private fun openAppLink(context: Context, nonce: String, pkHash: String) {
        var baseStr = config.verifyblindAppLinkBase.trimEnd('/')
        if (!baseStr.endsWith("/request")) {
            baseStr += "/request"
        }

        val url = Uri.parse(baseStr)
            .buildUpon()
            .appendQueryParameter("nonce", nonce)
            .appendQueryParameter("pk_hash", pkHash)
            .build()

        Log.i(TAG, "[VerifyBlind SDK] App Link açılıyor: $url")

        val intent = Intent(Intent.ACTION_VIEW, url).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        try {
            intent.setPackage("com.verifyblind.mobile")
            context.startActivity(intent)
        } catch (e: android.content.ActivityNotFoundException) {
            Log.w(TAG, "[VerifyBlind SDK] VerifyBlind bulunamadı, tarayıcıya yönlendiriliyor.")
            intent.setPackage(null)
            try {
                context.startActivity(intent)
            } catch (e2: Exception) {
                Log.e(TAG, "[VerifyBlind SDK] Uygulama veya tarayıcı açılamadı!", e2)
                throw VerifyBlindException(
                    "VerifyBlind uygulaması veya tarayıcı açılamadı: ${e2.message}",
                    cause = e2,
                    code = ErrorCode.APP_LINK_FAILED
                )
            }
        }
    }
}
