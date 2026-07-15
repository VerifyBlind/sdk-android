package com.verifyblind.sdk.network

import android.util.Log
import android.net.Uri
import com.verifyblind.sdk.VerifyBlindConfig
import okhttp3.OkHttpClient
import okhttp3.CertificatePinner
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * SDK için OkHttp + Retrofit istemcisi.
 */
internal object NetworkClient {

    private const val TAG = "VerifyBlind_SDK"

    fun createPartnerService(config: VerifyBlindConfig): PartnerBackendService {
        if (config.skipSecurityChecks) {
            Log.w(
                TAG,
                "⚠️ [VerifyBlind SDK] I_ACCEPT_SECURITY_RISKS: skipSecurityChecks=true olarak " +
                "ayarlandı. Bu mod yalnızca geliştirme/test ortamı içindir. ÜRETİMDE KULLANMAYIN."
            )
        }

        val clientBuilder = buildBaseClient(config)

        if (!config.skipSecurityChecks && !config.certificatePins.isNullOrEmpty()) {
            val domain = Uri.parse(config.partnerBackendUrl).host
            if (domain != null) {
                val pinnerBuilder = CertificatePinner.Builder()
                config.certificatePins.forEach { pin -> pinnerBuilder.add(domain, pin) }
                clientBuilder.certificatePinner(pinnerBuilder.build())
                Log.d(TAG, "[VerifyBlind SDK] Certificate Pinning uygulandı: $domain")
            }
        }

        return Retrofit.Builder()
            .baseUrl(ensureTrailingSlash(config.partnerBackendUrl))
            .addConverterFactory(GsonConverterFactory.create())
            .client(clientBuilder.build())
            .build()
            .create(PartnerBackendService::class.java)
    }

    fun createRelayService(apiUrl: String): KimlikRelayService {
        val client = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()

        return Retrofit.Builder()
            .baseUrl(ensureTrailingSlash(apiUrl))
            .addConverterFactory(GsonConverterFactory.create())
            .client(client)
            .build()
            .create(KimlikRelayService::class.java)
    }

    private fun buildBaseClient(config: VerifyBlindConfig): OkHttpClient.Builder {
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.HEADERS
        }
        return OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .addInterceptor(loggingInterceptor)
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .addHeader("X-VerifyBlind-SDK", "android/${VerifyBlindVersion.VERSION}")
                    .build()
                chain.proceed(request)
            }
    }

    private fun ensureTrailingSlash(url: String): String =
        if (url.endsWith("/")) url else "$url/"
}

internal object VerifyBlindVersion {
    const val VERSION = "2.1.0"
}
