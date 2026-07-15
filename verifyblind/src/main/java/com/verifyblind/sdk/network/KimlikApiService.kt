package com.verifyblind.sdk.network

import com.verifyblind.sdk.model.PartnerBackendResponse
import com.verifyblind.sdk.model.PopResultResponse
import com.verifyblind.sdk.model.StartAuthRequest
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Url

/**
 * Partner'ın backend proxy'sine istek atan Retrofit interface (PoP Mode).
 * Proxy { public_key, validations } alır, X-API-Key ile VerifyBlind API'ye iletir, { nonce } döndürür.
 */
internal interface PartnerBackendService {

    @POST
    suspend fun startAuth(@Url url: String, @Body request: StartAuthRequest): Response<PartnerBackendResponse>
}

/**
 * VerifyBlind relay API'yi poll eden Retrofit interface (PoP Mode).
 * GET /api/pop/result/{nonce} — şifreli sonucu döndürür (tek seferlik okuma).
 */
internal interface KimlikRelayService {

    @GET("api/pop/result/{nonce}")
    suspend fun getPopResult(@Path("nonce") nonce: String): Response<PopResultResponse>
}
