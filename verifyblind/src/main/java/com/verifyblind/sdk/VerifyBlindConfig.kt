package com.verifyblind.sdk

/**
 * VerifyBlind Android SDK Konfigürasyonu (PoP Mode).
 *
 * Partner backend sadece API key ile proxy görevi görür; private key tutmaz.
 * Ephemeral RSA keypair tarayıcıda/istemcide üretilir.
 *
 * @param partnerBackendUrl Partner'ın backend base URL'si (proxy endpoint'i).
 *        Örnek: "https://partner.example.com/api/auth"
 *
 * @param generateEndpoint İşlem başlatma uç noktası (relative path).
 *        `partnerBackendUrl` değerine eklenir. Varsayılan: "." (Base URL'in kendisi)
 *        Örnek: "generate"
 *
 * @param verifyblindAppLinkBase VerifyBlind deep link base URL.
 *        Varsayılan: "https://app.verifyblind.com/request"
 *
 * @param verifyblindApiUrl VerifyBlind relay API URL'si (sonuç polling için).
 *        Varsayılan: "https://api.verifyblind.com"
 *
 * @param skipSecurityChecks UYARI: Yalnızca geliştirme/test ortamı içindir.
 *        `true` olarak ayarlandığında Certificate Pinning devre dışı bırakılır.
 *        ÜRETİM ORTAMINDA ASLA `true` kullanmayınız.
 */
data class VerifyBlindConfig(
    val partnerBackendUrl: String,
    val generateEndpoint: String = ".",
    val verifyblindAppLinkBase: String,
    val verifyblindApiUrl: String = "https://api.verifyblind.com",
    val skipSecurityChecks: Boolean = false,
    val certificatePins: List<String>? = null,
    val cloudProjectNumber: Long? = null
) {
    init {
        require(partnerBackendUrl.isNotBlank()) { "partnerBackendUrl boş olamaz." }
    }
}
