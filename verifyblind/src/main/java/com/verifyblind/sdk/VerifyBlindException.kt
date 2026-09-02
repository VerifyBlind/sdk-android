package com.verifyblind.sdk

/**
 * VerifyBlind SDK tarafından fırlatılan özel exception sınıfı.
 */
class VerifyBlindException(
    message: String,
    cause: Throwable? = null,
    val code: ErrorCode = ErrorCode.UNKNOWN,
    /**
     * USER_CANCELLED durumunda partnere bildirilen sebep kodu.
     * Olası değerler: user_cancelled, no_card_registered, user_declined,
     * fingerprint_failed, session_expired. Geriye dönük uyumluluk için
     * bilinmeyen değerleri user_cancelled gibi ele alın.
     */
    val cancelReason: String? = null
) : Exception(message, cause) {

    enum class ErrorCode {
        /** Cihazın dış IP adresi alınamadı */
        IP_FETCH_FAILED,
        /** Partner backend'e ağ hatası nedeniyle ulaşılamadı */
        NETWORK_ERROR,
        /** Partner backend hata yanıtı döndürdü */
        PARTNER_BACKEND_ERROR,
        /** Partner backend yanıtı beklenen formatta değil (nonce/pkHash eksik) */
        INVALID_RESPONSE,
        /** App Link açılamadı (VerifyBlind uygulaması yüklü değil?) */
        APP_LINK_FAILED,
        /** Kullanıcı kimlik doğrulamayı iptal etti (consent veya parmak izi adımında) */
        USER_CANCELLED,
        /** Bilinmeyen hata */
        UNKNOWN
    }
}
