package com.verifyblind.sdk.model

/**
 * VerifyBlind işlemi başlatma sonucu.
 *
 * @param nonce API'ye kaydedilen nonce. App Link URL'sinde kullanılır.
 * @param pk_hash Ephemeral public key hash'i. App Link URL'sinde kullanılır.
 */
data class StartAuthResult(
    val nonce: String,
    val pk_hash: String,
    val validations: Map<String, Any>? = null
)

/**
 * Partner backend proxy'den dönen yanıt (PoP Mode).
 * Proxy { public_key, validations } alır, VerifyBlind API'ye iletir, { nonce } döndürür.
 */
data class PartnerBackendResponse(
    val nonce: String?,
    val error: String?
)

/**
 * SDK'nın partner backend'e gönderdiği istek (PoP Mode).
 * ```json
 * { "public_key": "...", "validations": {...} }
 * ```
 */
data class StartAuthRequest(
    val public_key: String,
    val integrity_token: String? = null,
    val validations: Map<String, Any>? = null,
    val custom_data: Map<String, Any>? = null
)

/**
 * VerifyBlind relay'den poll sonucu.
 * GET /api/pop/result/{nonce}
 */
data class PopResultResponse(
    val status: String,
    val encrypted_response: EncryptedResponsePayload?,
    val error: String?,
    /** İptal durumunda partnere bildirilen sebep kodu. Bkz. VerifyBlindException.cancelReason. */
    val reason: String? = null
)

/**
 * Enclave'den gelen şifreli yanıt.
 */
data class EncryptedResponsePayload(
    val enc_key: String,
    val blob: String
)
